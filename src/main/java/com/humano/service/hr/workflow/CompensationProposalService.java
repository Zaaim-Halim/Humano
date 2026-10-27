package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.BonusType;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.hr.CompensationProposal;
import com.humano.domain.hr.MeritGuidelines;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.Position;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.PositionChangeRequest;
import com.humano.dto.hr.workflow.requests.CompensationProposalRequest;
import com.humano.dto.hr.workflow.requests.GenerateCompensationProposalsRequest;
import com.humano.dto.hr.workflow.requests.MeritGuidelinesRequest;
import com.humano.dto.hr.workflow.requests.ReturnCompensationProposalRequest;
import com.humano.dto.hr.workflow.responses.CompensationProposalResponse;
import com.humano.dto.hr.workflow.responses.CompensationReviewCycle;
import com.humano.dto.hr.workflow.responses.CompensationReviewResponse;
import com.humano.dto.hr.workflow.responses.MeritGuidelinesResponse;
import com.humano.dto.payroll.request.AwardBonusRequest;
import com.humano.repository.hr.CompensationProposalRepository;
import com.humano.repository.hr.MeritGuidelinesRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.PositionChangeService;
import com.humano.service.payroll.BonusService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compensation review: pay and promotion decisions following a regular review cycle.
 *
 * <p>The compensation team (holders of {@code MANAGE_COMPENSATION}) <b>drafts</b> one proposal per
 * shared, rated review — never for an open dispute or someone leaving — with no increase prefilled:
 * a new base salary, a promotion and/or a one-off bonus, from a date, or nothing. The <b>merit
 * matrix</b> suggests an increase range per rating; going outside it is flagged, never refused.
 *
 * <p>A proposal is <b>submitted</b>, then <b>approved</b> by someone else holding the permission than
 * whoever last prepared it (drafted, edited or filled it from the matrix) or submitted it — four eyes —
 * and finally <b>applied</b> once: through {@link PositionChangeService} (the salary record is
 * split on the date and the history records it) and {@link BonusService} (an unpaid bonus that payroll
 * pays in the period of its date). Either can go back to draft with a comment, which is also the way
 * out when the date can no longer be applied. Nobody acts on their own proposal.
 *
 * <p>A proposal keeps the pay and position in effect when it was last prepared; applying refuses when
 * they changed since, so nothing is applied on top of a change the approver never saw. Ratings never
 * change pay by themselves.
 */
@Service
@Transactional("tenantTransactionManager")
public class CompensationProposalService {

    private static final Logger log = LoggerFactory.getLogger(CompensationProposalService.class);
    private static final String ENTITY_NAME = "compensationProposal";
    private static final String AUDIT_TARGET = "CompensationProposal";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH);
    private static final Set<EmployeeStatus> STAYING = EnumSet.of(EmployeeStatus.ACTIVE, EmployeeStatus.ONBOARDING);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * The signed-in user; they hold {@code MANAGE_COMPENSATION}, and {@code bonuses} says whether they may
     * also award bonuses ({@code MANAGE_BONUSES}).
     */
    public record Actor(UUID id, boolean bonuses) {}

    private final CompensationProposalRepository proposalRepository;
    private final MeritGuidelinesRepository guidelinesRepository;
    private final ReviewCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final CompensationRepository compensationRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PositionChangeService positionChangeService;
    private final BonusService bonusService;
    private final AuditEventService auditEventService;

    public CompensationProposalService(
        CompensationProposalRepository proposalRepository,
        MeritGuidelinesRepository guidelinesRepository,
        ReviewCycleRepository cycleRepository,
        PerformanceReviewRepository reviewRepository,
        EmployeeRepository employeeRepository,
        PositionRepository positionRepository,
        CompensationRepository compensationRepository,
        PayrollRunRepository payrollRunRepository,
        PositionChangeService positionChangeService,
        BonusService bonusService,
        AuditEventService auditEventService
    ) {
        this.proposalRepository = proposalRepository;
        this.guidelinesRepository = guidelinesRepository;
        this.cycleRepository = cycleRepository;
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
        this.positionRepository = positionRepository;
        this.compensationRepository = compensationRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.positionChangeService = positionChangeService;
        this.bonusService = bonusService;
        this.auditEventService = auditEventService;
    }

    // ---------------------------------------------------------------- merit matrix

    /** The merit matrix (the defaults until it is first saved). */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public MeritGuidelinesResponse guidelines() {
        return toResponse(currentGuidelines());
    }

    /** Saves the merit matrix: every rating once, each range from low to high. */
    public MeritGuidelinesResponse saveGuidelines(MeritGuidelinesRequest request) {
        Set<Integer> ratings = request.bands().stream().map(MeritGuidelinesRequest.Band::rating).collect(Collectors.toSet());
        if (ratings.size() != 5) {
            throw new BadRequestAlertException("Give one range for each rating from 1 to 5", ENTITY_NAME, "guidelineratings");
        }
        MeritGuidelines guidelines = currentGuidelines();
        for (MeritGuidelinesRequest.Band band : request.bands()) {
            if (band.minPercent().compareTo(band.maxPercent()) > 0) {
                throw new BadRequestAlertException(
                    "The range for rating " + band.rating() + " starts above where it ends",
                    ENTITY_NAME,
                    "guidelinerange"
                );
            }
            guidelines.setRange(band.rating(), band.minPercent(), band.maxPercent());
        }
        return toResponse(guidelinesRepository.save(guidelines));
    }

    // ---------------------------------------------------------------- reads

    /** The regular cycles, latest first, with how many proposals each has. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<CompensationReviewCycle> cycles() {
        return cycleRepository
            .findByTypeOrderByReviewPeriodEndDescStartDateDesc(ReviewCycleType.REGULAR)
            .stream()
            .map(c ->
                new CompensationReviewCycle(
                    c.getId(),
                    c.getName(),
                    c.getPhase(),
                    c.getReviewPeriodStart(),
                    c.getReviewPeriodEnd(),
                    proposalRepository.countByCycleId(c.getId())
                )
            )
            .toList();
    }

    /** A cycle's proposals except one's own, by employee name, with their totals and what is still to come. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public CompensationReviewResponse review(UUID cycleId, Actor actor) {
        ReviewCycle cycle = findRegularCycle(cycleId);
        MeritGuidelines guidelines = currentGuidelines();
        // One's own proposal stays out of one's view (and totals): someone else handles it.
        List<CompensationProposal> proposals = proposalRepository.findByCycleId(cycleId).stream().filter(p -> !isOwn(p, actor)).toList();
        Map<UUID, List<Compensation>> salaries = salariesOf(proposals.stream().map(p -> p.getEmployee().getId()).toList());

        List<CompensationProposalResponse> rows = proposals
            .stream()
            .sorted(Comparator.comparing(p -> fullName(p.getEmployee()).toLowerCase()))
            .map(p -> toResponse(p, guidelines, actor, changedSince(p, salaries.getOrDefault(p.getEmployee().getId(), List.of()))))
            .toList();

        Set<UUID> proposed = new HashSet<>(proposalRepository.findReviewIdsByCycleId(cycleId));
        int eligible = 0;
        int waiting = 0;
        int disputed = 0;
        for (PerformanceReview review : reviewRepository.findByCycleId(cycleId)) {
            if (proposed.contains(review.getId()) || review.getStatus() == PerformanceReviewStatus.EXCLUDED) {
                continue;
            }
            if (review.getStatus() != PerformanceReviewStatus.SHARED || review.getRating() == null) {
                waiting++;
            } else if (openDispute(review)) {
                disputed++;
            } else if (STAYING.contains(review.getEmployee().getStatus())) {
                eligible++;
            }
        }
        return new CompensationReviewResponse(
            cycle.getId(),
            cycle.getName(),
            cycle.getPhase(),
            cycle.getReviewPeriodStart(),
            cycle.getReviewPeriodEnd(),
            eligible,
            waiting,
            disputed,
            rows,
            totals(proposals)
        );
    }

    // ---------------------------------------------------------------- drafting

    /**
     * Drafts a proposal for each shared, rated review of the cycle that has none, skipping open disputes
     * and people leaving. No increase is prefilled. Returns how many were drafted.
     */
    public int generate(UUID cycleId, GenerateCompensationProposalsRequest request, Actor actor) {
        ReviewCycle cycle = findRegularCycle(cycleId);
        Set<UUID> proposed = new HashSet<>(proposalRepository.findReviewIdsByCycleId(cycleId));
        List<PerformanceReview> reviews = reviewRepository
            .findByCycleIdAndStatusIn(cycleId, List.of(PerformanceReviewStatus.SHARED))
            .stream()
            .filter(r -> r.getRating() != null && !proposed.contains(r.getId()) && !openDispute(r))
            .filter(r -> STAYING.contains(r.getEmployee().getStatus()))
            .toList();
        Map<UUID, List<Compensation>> salaries = salariesOf(reviews.stream().map(r -> r.getEmployee().getId()).toList());

        for (PerformanceReview review : reviews) {
            Employee employee = review.getEmployee();
            CompensationProposal proposal = new CompensationProposal();
            proposal.setCycle(cycle);
            proposal.setReview(review);
            proposal.setEmployee(employee);
            proposal.setRating(review.getRating());
            proposal.setPromotionRecommended(review.isRecommendForPromotion());
            proposal.setEffectiveDate(request.effectiveDate());
            proposal.setPreparedById(actor.id());
            snapshot(proposal, salaries.getOrDefault(employee.getId(), List.of()));
            proposalRepository.save(proposal);
        }
        if (!reviews.isEmpty()) {
            auditEventService.record(
                "COMPENSATION_PROPOSALS_DRAFTED",
                "ReviewCycle",
                cycleId.toString(),
                Map.<String, Object>of("count", reviews.size(), "effectiveDate", request.effectiveDate().toString())
            );
        }
        log.info("Drafted {} compensation proposals for cycle {}", reviews.size(), cycleId);
        return reviews.size();
    }

    /**
     * Sets the merit matrix's midpoint as the new salary of the cycle's drafts that have none yet (a
     * zero midpoint leaves pay as is). Returns how many changed.
     */
    public int useGuidance(UUID cycleId, Actor actor) {
        findRegularCycle(cycleId);
        MeritGuidelines guidelines = currentGuidelines();
        int changed = 0;
        for (CompensationProposal p : proposalRepository.findByCycleId(cycleId)) {
            if (
                p.getStatus() != CompensationProposalStatus.DRAFT ||
                isOwn(p, actor) ||
                p.getNewSalary() != null ||
                p.getCurrentSalary() == null
            ) {
                continue;
            }
            BigDecimal[] range = guidelines.rangeFor(p.getRating());
            BigDecimal midpoint = range[0].add(range[1]).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            if (midpoint.signum() > 0) {
                p.setNewSalary(
                    p
                        .getCurrentSalary()
                        .multiply(BigDecimal.ONE.add(midpoint.divide(HUNDRED, 6, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP)
                );
                p.setPreparedById(actor.id());
                changed++;
            }
        }
        return changed;
    }

    /** Changes a draft; the pay and position in effect on its date are taken again. */
    public CompensationProposalResponse update(UUID proposalId, CompensationProposalRequest request, Actor actor) {
        CompensationProposal p = findProposal(proposalId);
        requireNotOwn(p, actor);
        requireStatus(p, CompensationProposalStatus.DRAFT, "Only a draft can be changed");
        Employee employee = p.getEmployee();
        if (employee.getStartDate() != null && request.effectiveDate().isBefore(employee.getStartDate())) {
            throw new BadRequestAlertException("The change can't take effect before the hire date", ENTITY_NAME, "beforestart");
        }
        p.setEffectiveDate(request.effectiveDate());
        snapshot(p, salariesOf(List.of(employee.getId())).getOrDefault(employee.getId(), List.of()));

        if ((request.newSalary() != null || request.bonusAmount() != null) && p.getCurrentSalary() == null) {
            throw new BadRequestAlertException(
                "This person has no salary record on that date: set their pay on the Compensation tab first.",
                ENTITY_NAME,
                "nocompensation"
            );
        }
        Position newPosition = null;
        if (request.newPositionId() != null && !Objects.equals(request.newPositionId(), idOf(p.getCurrentPosition()))) {
            newPosition = positionRepository
                .findById(request.newPositionId())
                .orElseThrow(() -> EntityNotFoundException.create("Position", request.newPositionId()));
        }
        p.setNewSalary(request.newSalary());
        p.setNewPosition(newPosition);
        p.setBonusAmount(request.bonusAmount());
        p.setJustification(blankToNull(request.justification()));
        p.setPreparedById(actor.id());
        return toResponse(p, currentGuidelines(), actor, false);
    }

    /** Deletes a draft; drafting the cycle again recreates it. */
    public void delete(UUID proposalId, Actor actor) {
        CompensationProposal p = findProposal(proposalId);
        requireNotOwn(p, actor);
        requireStatus(p, CompensationProposalStatus.DRAFT, "Only a draft can be deleted");
        proposalRepository.delete(p);
    }

    // ---------------------------------------------------------------- approval

    /** Sends a draft for approval. */
    public CompensationProposalResponse submit(UUID proposalId, Actor actor) {
        CompensationProposal p = findProposal(proposalId);
        submit(p, actor);
        return toResponse(p, currentGuidelines(), actor, false);
    }

    /** Submits every draft of the cycle the signed-in user may submit. Returns how many. */
    public int submitAll(UUID cycleId, Actor actor) {
        findRegularCycle(cycleId);
        List<CompensationProposal> drafts = proposalRepository
            .findByCycleId(cycleId)
            .stream()
            .filter(p -> p.getStatus() == CompensationProposalStatus.DRAFT && !isOwn(p, actor))
            .toList();
        drafts.forEach(p -> submit(p, actor));
        return drafts.size();
    }

    /** Approves a submitted proposal — someone else than whoever prepared or submitted it. */
    public CompensationProposalResponse approve(UUID proposalId, Actor actor) {
        CompensationProposal p = findProposal(proposalId);
        approve(p, actor);
        return toResponse(p, currentGuidelines(), actor, false);
    }

    /** Approves every submitted proposal of the cycle the signed-in user may approve. Returns how many. */
    public int approveAll(UUID cycleId, Actor actor) {
        findRegularCycle(cycleId);
        List<CompensationProposal> submitted = proposalRepository
            .findByCycleId(cycleId)
            .stream()
            .filter(p -> p.getStatus() == CompensationProposalStatus.SUBMITTED && canApprove(p, actor))
            .toList();
        submitted.forEach(p -> approve(p, actor));
        return submitted.size();
    }

    /** Sends a submitted or approved proposal back to draft, saying what to change. */
    public CompensationProposalResponse returnToDraft(UUID proposalId, ReturnCompensationProposalRequest request, Actor actor) {
        CompensationProposal p = findProposal(proposalId);
        requireNotOwn(p, actor);
        if (p.getStatus() != CompensationProposalStatus.SUBMITTED && p.getStatus() != CompensationProposalStatus.APPROVED) {
            throw new BadRequestAlertException("Only a submitted or approved proposal goes back to draft", ENTITY_NAME, "badstatus");
        }
        p.setStatus(CompensationProposalStatus.DRAFT);
        p.setReturnComment(request.comment().trim());
        clearApproval(p);
        audit("COMPENSATION_PROPOSAL_RETURNED", p, Map.of("comment", p.getReturnComment()));
        return toResponse(p, currentGuidelines(), actor, false);
    }

    // ---------------------------------------------------------------- apply

    /**
     * Applies an approved proposal, once: the new salary and/or position through the position-change
     * service and the bonus as an unpaid bonus payroll pays in the period of the date. Everything is
     * checked first, and any refusal on the way rolls the whole of it back.
     */
    public CompensationProposalResponse apply(UUID proposalId, Actor actor) {
        CompensationProposal p = proposalRepository
            .findForUpdate(proposalId)
            .orElseThrow(() -> EntityNotFoundException.create("CompensationProposal", proposalId));
        requireNotOwn(p, actor);
        requireStatus(p, CompensationProposalStatus.APPROVED, "Only an approved proposal can be applied");
        Employee employee = p.getEmployee();
        if (!STAYING.contains(employee.getStatus())) {
            throw new BadRequestAlertException(
                "This person is leaving or has left: return the proposal to draft",
                ENTITY_NAME,
                "notworking"
            );
        }
        boolean hasBonus = p.getBonusAmount() != null && p.getBonusAmount().signum() > 0;
        if (hasBonus && !actor.bonuses()) {
            throw new AccessDeniedException("Applying a bonus needs the permission to manage bonuses");
        }
        LocalDate date = p.getEffectiveDate();
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestAlertException(
                "It takes effect on " + DATE.format(date) + ": apply it on or after that day.",
                ENTITY_NAME,
                "futuredate"
            );
        }
        LocalDate settled = payrollRunRepository.findLatestPeriodEndWithStatusIn(List.of(RunStatus.APPROVED, RunStatus.POSTED));
        if (settled != null && !date.isAfter(settled)) {
            throw new BadRequestAlertException(
                "Payroll up to " +
                DATE.format(settled) +
                " is already approved or posted: return the proposal to draft and move its date after it.",
                ENTITY_NAME,
                "payrollsettled"
            );
        }
        if (changedSince(p, salariesOf(List.of(employee.getId())).getOrDefault(employee.getId(), List.of()))) {
            throw new BadRequestAlertException(
                "The pay or position in effect changed since this was prepared: return it to draft and review it again.",
                ENTITY_NAME,
                "paychanged"
            );
        }

        boolean salaryChanges = p.getNewSalary() != null && p.getNewSalary().compareTo(p.getCurrentSalary()) != 0;
        boolean positionChanges = p.getNewPosition() != null && !Objects.equals(idOf(p.getNewPosition()), idOf(employee.getPosition()));
        String reason = truncate(p.getCycle().getName() + (positionChanges ? " — promotion" : " — merit increase"), 255);
        if (salaryChanges || positionChanges) {
            p.setPositionChangeId(
                positionChangeService
                    .change(
                        employee.getId(),
                        new PositionChangeRequest(
                            positionChanges ? PositionChangeType.PROMOTION : PositionChangeType.OTHER,
                            date,
                            positionChanges ? p.getNewPosition().getId() : null,
                            null,
                            salaryChanges ? p.getNewSalary() : null,
                            reason
                        )
                    )
                    .id()
            );
        }
        if (hasBonus) {
            p.setBonusId(
                bonusService
                    .awardBonus(
                        new AwardBonusRequest(
                            employee.getId(),
                            BonusType.PERFORMANCE,
                            p.getBonusAmount(),
                            p.getCurrency().getId(),
                            date,
                            date,
                            truncate(p.getCycle().getName() + " — performance bonus", 500),
                            false
                        )
                    )
                    .id()
            );
        }
        p.setStatus(CompensationProposalStatus.APPLIED);
        p.setAppliedByName(actorName(actor));
        p.setAppliedAt(Instant.now());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", employee.getId().toString());
        payload.put("effectiveDate", date.toString());
        payload.put("salaryFrom", p.getCurrentSalary());
        payload.put("salaryTo", salaryChanges ? p.getNewSalary() : null);
        payload.put("positionTo", positionChanges ? p.getNewPosition().getName() : null);
        payload.put("bonus", hasBonus ? p.getBonusAmount() : null);
        audit("COMPENSATION_PROPOSAL_APPLIED", p, payload);
        log.info("Applied compensation proposal {} for employee {} effective {}", p.getId(), employee.getId(), date);
        return toResponse(p, currentGuidelines(), actor, false);
    }

    // ---------------------------------------------------------------- helpers

    private void submit(CompensationProposal p, Actor actor) {
        requireNotOwn(p, actor);
        requireStatus(p, CompensationProposalStatus.DRAFT, "Only a draft can be submitted");
        if (p.getNewSalary() != null && p.getCurrentSalary() == null) {
            throw new BadRequestAlertException("This person has no salary record on that date", ENTITY_NAME, "nocompensation");
        }
        p.setStatus(CompensationProposalStatus.SUBMITTED);
        p.setSubmittedById(actor.id());
        p.setSubmittedByName(actorName(actor));
        p.setSubmittedAt(Instant.now());
        audit("COMPENSATION_PROPOSAL_SUBMITTED", p, Map.of());
    }

    private void approve(CompensationProposal p, Actor actor) {
        requireNotOwn(p, actor);
        requireStatus(p, CompensationProposalStatus.SUBMITTED, "Only a submitted proposal can be approved");
        if (!canApprove(p, actor)) {
            throw new AccessDeniedException("Someone other than whoever prepared or submitted it approves it");
        }
        p.setStatus(CompensationProposalStatus.APPROVED);
        p.setApprovedById(actor.id());
        p.setApprovedByName(actorName(actor));
        p.setApprovedAt(Instant.now());
        audit("COMPENSATION_PROPOSAL_APPROVED", p, Map.of());
    }

    private static boolean canApprove(CompensationProposal p, Actor actor) {
        return !isOwn(p, actor) && !Objects.equals(p.getSubmittedById(), actor.id()) && !Objects.equals(p.getPreparedById(), actor.id());
    }

    private static void clearApproval(CompensationProposal p) {
        p.setSubmittedById(null);
        p.setSubmittedByName(null);
        p.setSubmittedAt(null);
        p.setApprovedById(null);
        p.setApprovedByName(null);
        p.setApprovedAt(null);
    }

    /** Keeps the pay and position in effect on the proposal's date. */
    private static void snapshot(CompensationProposal p, List<Compensation> salaries) {
        Compensation salary = activeSalary(salaries, p.getEffectiveDate());
        p.setCurrentSalary(salary != null ? salary.getBaseAmount() : null);
        p.setCurrency(salary != null ? salary.getCurrency() : null);
        p.setBasis(salary != null ? salary.getBasis() : null);
        p.setCurrentPosition(p.getEmployee().getPosition());
    }

    /** Whether the pay or position in effect on the proposal's date differ from what it was prepared on. */
    private static boolean changedSince(CompensationProposal p, List<Compensation> salaries) {
        if (p.getStatus() == CompensationProposalStatus.APPLIED) {
            return false;
        }
        Compensation salary = activeSalary(salaries, p.getEffectiveDate());
        if (!Objects.equals(idOf(p.getCurrentPosition()), idOf(p.getEmployee().getPosition()))) {
            return true;
        }
        if (salary == null) {
            return p.getCurrentSalary() != null;
        }
        return (
            p.getCurrentSalary() == null ||
            salary.getBaseAmount().compareTo(p.getCurrentSalary()) != 0 ||
            salary.getBasis() != p.getBasis() ||
            !Objects.equals(salary.getCurrency().getId(), p.getCurrency() != null ? p.getCurrency().getId() : null)
        );
    }

    /** The salary record in effect on {@code date} (latest-starting wins, as in payroll). */
    private static Compensation activeSalary(List<Compensation> salaries, LocalDate date) {
        return salaries
            .stream()
            .filter(c -> c.getEffectiveFrom() != null && !c.getEffectiveFrom().isAfter(date))
            .filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(date))
            .max(Comparator.comparing(Compensation::getEffectiveFrom).thenComparing(Compensation::getId, Comparator.reverseOrder()))
            .orElse(null);
    }

    private Map<UUID, List<Compensation>> salariesOf(List<UUID> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        return compensationRepository
            .findAllByEmployeeIds(employeeIds)
            .stream()
            .collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
    }

    /** One line per currency and basis: current and proposed base pay of those with a salary, and bonuses. */
    private static List<CompensationReviewResponse.Total> totals(List<CompensationProposal> proposals) {
        Map<String, List<CompensationProposal>> groups = new LinkedHashMap<>();
        proposals
            .stream()
            .filter(p -> p.getCurrentSalary() != null && p.getCurrency() != null)
            .sorted(
                Comparator.comparing((CompensationProposal p) -> p.getCurrency().getCode().name()).thenComparing(p -> p.getBasis().name())
            )
            .forEach(p ->
                groups.computeIfAbsent(p.getCurrency().getCode().name() + "|" + p.getBasis().name(), k -> new ArrayList<>()).add(p)
            );
        List<CompensationReviewResponse.Total> totals = new ArrayList<>();
        for (List<CompensationProposal> group : groups.values()) {
            BigDecimal current = BigDecimal.ZERO;
            BigDecimal proposed = BigDecimal.ZERO;
            BigDecimal bonuses = BigDecimal.ZERO;
            for (CompensationProposal p : group) {
                current = current.add(p.getCurrentSalary());
                proposed = proposed.add(p.getNewSalary() != null ? p.getNewSalary() : p.getCurrentSalary());
                bonuses = bonuses.add(p.getBonusAmount() != null ? p.getBonusAmount() : BigDecimal.ZERO);
            }
            CompensationProposal first = group.get(0);
            totals.add(
                new CompensationReviewResponse.Total(
                    first.getCurrency().getCode().name(),
                    first.getBasis(),
                    group.size(),
                    current,
                    proposed,
                    percent(current, proposed),
                    bonuses
                )
            );
        }
        return totals;
    }

    private CompensationProposalResponse toResponse(CompensationProposal p, MeritGuidelines guidelines, Actor actor, boolean payChanged) {
        BigDecimal[] range = guidelines.rangeFor(p.getRating());
        BigDecimal increase = p.getCurrentSalary() != null && p.getNewSalary() != null
            ? percent(p.getCurrentSalary(), p.getNewSalary())
            : null;
        BigDecimal effective = increase != null ? increase : p.getCurrentSalary() != null ? BigDecimal.ZERO : null;
        boolean outside = effective != null && (effective.compareTo(range[0]) < 0 || effective.compareTo(range[1]) > 0);
        boolean own = isOwn(p, actor);
        CompensationProposalStatus s = p.getStatus();
        Employee e = p.getEmployee();
        return new CompensationProposalResponse(
            p.getId(),
            p.getCycle().getId(),
            p.getReview().getId(),
            e.getId(),
            fullName(e),
            e.getJobTitle(),
            p.getRating(),
            p.isPromotionRecommended(),
            s,
            p.getEffectiveDate(),
            p.getCurrentSalary(),
            p.getCurrency() != null ? p.getCurrency().getCode().name() : null,
            p.getBasis(),
            idOf(p.getCurrentPosition()),
            p.getCurrentPosition() != null ? p.getCurrentPosition().getName() : null,
            p.getNewSalary(),
            increase,
            range[0],
            range[1],
            outside,
            idOf(p.getNewPosition()),
            p.getNewPosition() != null ? p.getNewPosition().getName() : null,
            p.getBonusAmount(),
            p.getJustification(),
            p.getReturnComment(),
            p.getSubmittedByName(),
            p.getSubmittedAt(),
            p.getApprovedByName(),
            p.getApprovedAt(),
            p.getAppliedByName(),
            p.getAppliedAt(),
            payChanged,
            new CompensationProposalResponse.Actions(
                !own && s == CompensationProposalStatus.DRAFT,
                !own && s == CompensationProposalStatus.DRAFT,
                !own && s == CompensationProposalStatus.DRAFT,
                s == CompensationProposalStatus.SUBMITTED && canApprove(p, actor),
                !own && (s == CompensationProposalStatus.SUBMITTED || s == CompensationProposalStatus.APPROVED),
                !own &&
                s == CompensationProposalStatus.APPROVED &&
                !payChanged &&
                (actor.bonuses() || p.getBonusAmount() == null || p.getBonusAmount().signum() == 0)
            )
        );
    }

    private static MeritGuidelinesResponse toResponse(MeritGuidelines g) {
        List<MeritGuidelinesResponse.Band> bands = new ArrayList<>();
        for (int rating = 1; rating <= 5; rating++) {
            BigDecimal[] range = g.rangeFor(rating);
            bands.add(new MeritGuidelinesResponse.Band(rating, range[0], range[1]));
        }
        return new MeritGuidelinesResponse(bands);
    }

    /** The increase from {@code from} to {@code to}, in percent with two decimals. */
    private static BigDecimal percent(BigDecimal from, BigDecimal to) {
        if (from.signum() == 0) {
            return null;
        }
        return to.subtract(from).multiply(HUNDRED).divide(from, 2, RoundingMode.HALF_UP);
    }

    private MeritGuidelines currentGuidelines() {
        return guidelinesRepository.findAll().stream().findFirst().orElseGet(MeritGuidelines::new);
    }

    private ReviewCycle findRegularCycle(UUID cycleId) {
        ReviewCycle cycle = cycleRepository.findById(cycleId).orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));
        if (cycle.getType() != ReviewCycleType.REGULAR) {
            throw new BadRequestAlertException("Pay decisions follow regular review cycles only", ENTITY_NAME, "notregular");
        }
        return cycle;
    }

    private CompensationProposal findProposal(UUID proposalId) {
        return proposalRepository
            .findWithDetailsById(proposalId)
            .orElseThrow(() -> EntityNotFoundException.create("CompensationProposal", proposalId));
    }

    private static boolean openDispute(PerformanceReview review) {
        return review.isDisputed() && review.getDisputeResolvedAt() == null;
    }

    private static boolean isOwn(CompensationProposal p, Actor actor) {
        return Objects.equals(p.getEmployee().getId(), actor.id());
    }

    private static void requireNotOwn(CompensationProposal p, Actor actor) {
        if (isOwn(p, actor)) {
            throw new AccessDeniedException("Nobody acts on their own compensation proposal");
        }
    }

    private static void requireStatus(CompensationProposal p, CompensationProposalStatus status, String message) {
        if (p.getStatus() != status) {
            throw new BadRequestAlertException(message, ENTITY_NAME, "badstatus");
        }
    }

    private void audit(String action, CompensationProposal p, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cycleId", p.getCycle().getId().toString());
        payload.put("employeeId", p.getEmployee().getId().toString());
        payload.putAll(details);
        auditEventService.record(action, AUDIT_TARGET, p.getId().toString(), payload);
    }

    private String actorName(Actor actor) {
        return employeeRepository.findById(actor.id()).map(CompensationProposalService::fullName).map(n -> truncate(n, 100)).orElse(null);
    }

    private static UUID idOf(Position position) {
        return position != null ? position.getId() : null;
    }

    private static String fullName(Employee e) {
        return ((e.getFirstName() != null ? e.getFirstName() : "") + " " + (e.getLastName() != null ? e.getLastName() : "")).trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
