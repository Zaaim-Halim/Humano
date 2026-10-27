package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** Retention: what is due or held, deleting exactly what was shown, and the job only when turned on. */
class ReviewRetentionServiceTest {

    private final ReviewRetentionSettingsRepository settings = mock(ReviewRetentionSettingsRepository.class);
    private final ReviewCycleRepository cycles = mock(ReviewCycleRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final ImprovementPlanRepository plans = mock(ImprovementPlanRepository.class);
    private final CompensationProposalRepository proposals = mock(CompensationProposalRepository.class);
    private final ReviewCyclePurger purger = mock(ReviewCyclePurger.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final ReviewRetentionService service = new ReviewRetentionService(
        settings,
        cycles,
        reviews,
        plans,
        proposals,
        purger,
        audit,
        mock(PlatformTransactionManager.class)
    );

    @Test
    void theCutoffIsWholeYearsBeforeToday() {
        assertThat(ReviewRetentionService.cutoff(LocalDate.of(2026, 10, 27), 6)).isEqualTo(LocalDate.of(2020, 10, 27));
    }

    @Test
    void anArchivedSettledCycleIsDueAndAnUnarchivedOrDisputedOneIsHeld() {
        ReviewCycle settled = cycle(ReviewCyclePhase.ARCHIVED);
        ReviewCycle completed = cycle(ReviewCyclePhase.COMPLETED);
        ReviewCycle disputed = cycle(ReviewCyclePhase.ARCHIVED);
        listed(settled, completed, disputed);
        when(reviews.countByCycleIdAndDisputedTrueAndDisputeResolvedAtIsNull(disputed.getId())).thenReturn(2L);

        ReviewRetentionResponse view = service.get();

        assertThat(view.customized()).isFalse();
        assertThat(view.enabled()).isFalse();
        assertThat(view.retainYears()).isEqualTo(ReviewRetentionSettings.DEFAULT_RETAIN_YEARS);
        assertThat(view.due()).extracting(ReviewRetentionResponse.Cycle::id).containsExactly(settled.getId());
        assertThat(view.held())
            .extracting(c -> c.holds())
            .containsExactly(new ReviewRetentionResponse.Holds(true, 0, 0, 0), new ReviewRetentionResponse.Holds(false, 2, 0, 0));
    }

    @Test
    void aListOtherThanTheOneDueNowIsRefusedAndNothingIsDeleted() {
        ReviewCycle due = cycle(ReviewCyclePhase.ARCHIVED);
        listed(due);

        assertThatThrownBy(() -> service.purge(new PurgeReviewCyclesRequest(List.of(due.getId(), UUID.randomUUID()))))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("changed since you looked");
        verify(cycles, never()).findForUpdate(any());
        verifyNoInteractions(purger);
    }

    @Test
    void aCycleHeldBackOnceLockedIsLeftAlone() {
        ReviewCycle due = cycle(ReviewCyclePhase.ARCHIVED);
        listed(due);
        when(cycles.findForUpdate(due.getId())).thenReturn(Optional.of(due));
        // An unapplied proposal appears between the listing and the lock.
        when(proposals.countByCycleIdAndStatusNot(due.getId(), CompensationProposalStatus.APPLIED)).thenReturn(0L, 1L);

        ReviewRetentionPurgeResult result = service.purge(new PurgeReviewCyclesRequest(List.of(due.getId())));

        assertThat(result).isEqualTo(new ReviewRetentionPurgeResult(0, 0, 0));
        verifyNoInteractions(purger);
    }

    @Test
    void aDueCycleIsDeletedAndAudited() {
        ReviewCycle due = cycle(ReviewCyclePhase.ARCHIVED);
        listed(due);
        when(cycles.findForUpdate(due.getId())).thenReturn(Optional.of(due));
        when(purger.purge(due)).thenReturn(Map.of("reviews", 12));

        ReviewRetentionPurgeResult result = service.purge(new PurgeReviewCyclesRequest(List.of(due.getId())));

        assertThat(result).isEqualTo(new ReviewRetentionPurgeResult(1, 12, 0));
        verify(audit).record(eq("PERFORMANCE_CYCLE_PURGED"), eq("ReviewCycle"), eq(due.getId().toString()), anyMap());
    }

    @Test
    void theJobDeletesNothingUntilTurnedOn() {
        listed(cycle(ReviewCyclePhase.ARCHIVED));

        assertThat(service.purgeDue()).isEqualTo(new ReviewRetentionPurgeResult(0, 0, 0));
        verify(cycles, never()).findByReviewPeriodEndBeforeAndPhaseNotOrderByReviewPeriodEndAsc(any(), any());
        verifyNoInteractions(purger);
    }

    @Test
    void savingTheSettingsDeletesNothing() {
        when(settings.save(any())).thenAnswer(i -> {
            ReviewRetentionSettings s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        listed(cycle(ReviewCyclePhase.ARCHIVED));

        ReviewRetentionResponse view = service.save(new ReviewRetentionSettingsRequest(true, 3));

        assertThat(view.enabled()).isTrue();
        assertThat(view.retainYears()).isEqualTo(3);
        assertThat(view.due()).hasSize(1);
        verify(audit).record(eq("PERFORMANCE_RETENTION_UPDATED"), eq("ReviewRetentionSettings"), any(), anyMap());
        verifyNoInteractions(purger);
    }

    private void listed(ReviewCycle... listed) {
        when(cycles.findByReviewPeriodEndBeforeAndPhaseNotOrderByReviewPeriodEndAsc(any(), eq(ReviewCyclePhase.DRAFT))).thenReturn(
            List.of(listed)
        );
    }

    private static ReviewCycle cycle(ReviewCyclePhase phase) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setName("FY2015 review");
        cycle.setType(ReviewCycleType.REGULAR);
        cycle.setPhase(phase);
        cycle.setReviewPeriodStart(LocalDate.of(2015, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(2015, 12, 31));
        return cycle;
    }
}
