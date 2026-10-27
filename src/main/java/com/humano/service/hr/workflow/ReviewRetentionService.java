package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewRetentionSettings;
import com.humano.dto.hr.workflow.requests.PurgeReviewCyclesRequest;
import com.humano.dto.hr.workflow.requests.ReviewRetentionSettingsRequest;
import com.humano.dto.hr.workflow.responses.ReviewRetentionPurgeResult;
import com.humano.dto.hr.workflow.responses.ReviewRetentionResponse;
import com.humano.repository.hr.CompensationProposalRepository;
import com.humano.repository.hr.ImprovementPlanRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.ReviewRetentionSettingsRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BadRequestAlertException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retention of performance review records: how long they are kept, and deleting them after.
 *
 * <p>A cycle is <b>due</b> once its review period ended more than the retention period ago (whole
 * years, 6 by default), it is archived, and nothing about it is still open — no unresolved dispute,
 * no improvement plan in draft or running, no compensation proposal waiting to be applied. A cycle
 * past the period that isn't settled is <b>held</b>, and the retention page says why; it becomes
 * due once settled.
 *
 * <p>Due cycles are deleted whole (see {@link ReviewCyclePurger}) by HR from the retention page —
 * exactly the list they were shown — or, once HR turns it on, by a daily job. Each cycle goes in a
 * transaction of its own, locked and checked again first, and leaves one audit event with what it
 * deleted. Saving the settings never deletes anything. Goals, position history, bonuses, pay and the
 * audit log are not review records and stay.
 *
 * <p>The job keeps applying the settings to a tenant that no longer has the performance feature:
 * deleting on time is an obligation that doesn't end with the subscription.
 */
@Service
@Transactional("tenantTransactionManager")
public class ReviewRetentionService {

    private static final Logger log = LoggerFactory.getLogger(ReviewRetentionService.class);
    private static final String ENTITY_NAME = "reviewRetention";
    private static final Set<ImprovementPlanStatus> OPEN_PLANS = EnumSet.of(ImprovementPlanStatus.DRAFT, ImprovementPlanStatus.ACTIVE);

    /** Who started a deletion, as the audit event records it. */
    public enum Trigger {
        JOB,
        MANUAL,
    }

    private final ReviewRetentionSettingsRepository settingsRepository;
    private final ReviewCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final ImprovementPlanRepository planRepository;
    private final CompensationProposalRepository proposalRepository;
    private final ReviewCyclePurger purger;
    private final AuditEventService auditEventService;
    private final TransactionTemplate perCycleTx;

    public ReviewRetentionService(
        ReviewRetentionSettingsRepository settingsRepository,
        ReviewCycleRepository cycleRepository,
        PerformanceReviewRepository reviewRepository,
        ImprovementPlanRepository planRepository,
        CompensationProposalRepository proposalRepository,
        ReviewCyclePurger purger,
        AuditEventService auditEventService,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager
    ) {
        this.settingsRepository = settingsRepository;
        this.cycleRepository = cycleRepository;
        this.reviewRepository = reviewRepository;
        this.planRepository = planRepository;
        this.proposalRepository = proposalRepository;
        this.purger = purger;
        this.auditEventService = auditEventService;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.perCycleTx = tx;
    }

    /** The settings (the defaults until saved), with the cycles due and held today. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewRetentionResponse get() {
        return view(current(), LocalDate.now());
    }

    /** Replaces the settings. Deletes nothing: due cycles go on the next run, or when HR deletes them. */
    public ReviewRetentionResponse save(ReviewRetentionSettingsRequest request) {
        ReviewRetentionSettings settings = current();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", request.enabled());
        payload.put("retainYears", request.retainYears());
        payload.put("previousEnabled", settings.isEnabled());
        payload.put("previousRetainYears", settings.getRetainYears());

        settings.setEnabled(request.enabled());
        settings.setRetainYears(request.retainYears());
        ReviewRetentionSettings saved = settingsRepository.save(settings);
        auditEventService.record("PERFORMANCE_RETENTION_UPDATED", "ReviewRetentionSettings", saved.getId().toString(), payload);
        return view(saved, LocalDate.now());
    }

    /**
     * Deletes the cycles due now, when HR asks — exactly those they were shown: if the list changed
     * since (a cycle became due or was settled differently), nothing is deleted and they look again.
     */
    @Transactional(value = "tenantTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public ReviewRetentionPurgeResult purge(PurgeReviewCyclesRequest request) {
        ReviewRetentionSettings settings = current();
        LocalDate cutoff = cutoff(LocalDate.now(), settings.getRetainYears());
        List<UUID> due = view(settings, LocalDate.now()).due().stream().map(ReviewRetentionResponse.Cycle::id).toList();
        if (!new HashSet<>(due).equals(new HashSet<>(request.cycleIds()))) {
            throw new BadRequestAlertException(
                "The cycles due for deletion changed since you looked — review the list again",
                ENTITY_NAME,
                "retentionchanged"
            );
        }
        return run(due, cutoff, settings.getRetainYears(), Trigger.MANUAL);
    }

    /** The daily run: deletes the cycles due, if HR turned automatic deletion on. */
    @Transactional(value = "tenantTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public ReviewRetentionPurgeResult purgeDue() {
        ReviewRetentionSettings settings = current();
        if (!settings.isEnabled()) {
            return new ReviewRetentionPurgeResult(0, 0, 0);
        }
        LocalDate cutoff = cutoff(LocalDate.now(), settings.getRetainYears());
        List<UUID> due = view(settings, LocalDate.now()).due().stream().map(ReviewRetentionResponse.Cycle::id).toList();
        return run(due, cutoff, settings.getRetainYears(), Trigger.JOB);
    }

    /** Review periods that ended before this date are past the retention period. */
    static LocalDate cutoff(LocalDate today, int retainYears) {
        return today.minusYears(retainYears);
    }

    private ReviewRetentionPurgeResult run(List<UUID> cycleIds, LocalDate cutoff, int retainYears, Trigger trigger) {
        int cycles = 0;
        int failed = 0;
        long reviews = 0;
        for (UUID cycleId : cycleIds) {
            try {
                Map<String, Integer> counts = perCycleTx.execute(status -> purgeOne(cycleId, cutoff, retainYears, trigger));
                if (counts != null) {
                    cycles++;
                    reviews += counts.getOrDefault("reviews", 0);
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Could not delete review cycle {} under the retention period: {}", cycleId, e.getMessage(), e);
            }
        }
        return new ReviewRetentionPurgeResult(cycles, reviews, failed);
    }

    /** Deletes one cycle if it is still due once locked; empty when it no longer is. */
    private Map<String, Integer> purgeOne(UUID cycleId, LocalDate cutoff, int retainYears, Trigger trigger) {
        ReviewCycle cycle = cycleRepository.findForUpdate(cycleId).orElse(null);
        if (cycle == null || !cycle.getReviewPeriodEnd().isBefore(cutoff) || holds(cycle).any()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", cycle.getName());
        payload.put("type", cycle.getType().name());
        payload.put("periodStart", cycle.getReviewPeriodStart().toString());
        payload.put("periodEnd", cycle.getReviewPeriodEnd().toString());
        payload.put("trigger", trigger.name());
        payload.put("retainYears", retainYears);

        Map<String, Integer> counts = purger.purge(cycle);
        payload.putAll(counts);
        auditEventService.record("PERFORMANCE_CYCLE_PURGED", "ReviewCycle", cycleId.toString(), payload);
        log.info("Deleted review cycle {} ({}) under the retention period: {}", cycleId, trigger, counts);
        return counts;
    }

    private ReviewRetentionResponse view(ReviewRetentionSettings settings, LocalDate today) {
        LocalDate cutoff = cutoff(today, settings.getRetainYears());
        List<ReviewRetentionResponse.Cycle> due = new ArrayList<>();
        List<ReviewRetentionResponse.Cycle> held = new ArrayList<>();
        for (ReviewCycle cycle : cycleRepository.findByReviewPeriodEndBeforeAndPhaseNotOrderByReviewPeriodEndAsc(
            cutoff,
            ReviewCyclePhase.DRAFT
        )) {
            ReviewRetentionResponse.Holds holds = holds(cycle);
            ReviewRetentionResponse.Cycle row = new ReviewRetentionResponse.Cycle(
                cycle.getId(),
                cycle.getName(),
                cycle.getType(),
                cycle.getPhase(),
                cycle.getReviewPeriodStart(),
                cycle.getReviewPeriodEnd(),
                reviewRepository.countByCycleId(cycle.getId()),
                holds
            );
            (holds.any() ? held : due).add(row);
        }
        return new ReviewRetentionResponse(settings.isEnabled(), settings.getRetainYears(), settings.getId() != null, cutoff, due, held);
    }

    private ReviewRetentionResponse.Holds holds(ReviewCycle cycle) {
        UUID id = cycle.getId();
        return new ReviewRetentionResponse.Holds(
            cycle.getPhase() != ReviewCyclePhase.ARCHIVED,
            reviewRepository.countByCycleIdAndDisputedTrueAndDisputeResolvedAtIsNull(id),
            planRepository.countByReviewCycleIdAndStatusIn(id, OPEN_PLANS),
            proposalRepository.countByCycleIdAndStatusNot(id, CompensationProposalStatus.APPLIED)
        );
    }

    private ReviewRetentionSettings current() {
        return settingsRepository.findAll().stream().findFirst().orElseGet(ReviewRetentionSettings::new);
    }
}
