package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.hr.ReviewCadence;
import com.humano.domain.hr.ReviewCalendarSettings;
import com.humano.dto.hr.workflow.responses.ReviewCalendarResponse.CycleProposal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewCalendarServiceTest {

    private static final LocalDate MID_OCTOBER = LocalDate.of(2026, 10, 15);

    @Test
    void annualCycleOpensBeforeTheYearEndsAndChainsThePhases() {
        CycleProposal next = ReviewCalendarService.propose(new ReviewCalendarSettings(), MID_OCTOBER, (p, n) -> false);

        assertThat(next.name()).isEqualTo("2026 Annual Review");
        assertThat(next.reviewPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(next.reviewPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(next.startDate()).isEqualTo(LocalDate.of(2026, 12, 17));
        assertThat(next.selfAssessmentDeadline()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(next.managerReviewDeadline()).isEqualTo(LocalDate.of(2027, 1, 14));
        assertThat(next.calibrationDeadline()).isEqualTo(LocalDate.of(2027, 1, 21));
        assertThat(next.feedbackDeadline()).isEqualTo(LocalDate.of(2027, 2, 4));
        assertThat(next.endDate()).isEqualTo(next.feedbackDeadline());
    }

    @Test
    void withoutCalibrationFeedbackFollowsTheManagerReview() {
        ReviewCalendarSettings settings = new ReviewCalendarSettings();
        settings.setCalibrationDays(0);

        CycleProposal next = ReviewCalendarService.propose(settings, MID_OCTOBER, (p, n) -> false);

        assertThat(next.calibrationDeadline()).isNull();
        assertThat(next.feedbackDeadline()).isEqualTo(next.managerReviewDeadline().plusDays(14));
    }

    @Test
    void anUnreviewedPeriodThatJustEndedIsProposedFirstAndOpensToday() {
        LocalDate january = LocalDate.of(2027, 1, 20);

        CycleProposal next = ReviewCalendarService.propose(new ReviewCalendarSettings(), january, (p, n) -> false);

        assertThat(next.name()).isEqualTo("2026 Annual Review");
        assertThat(next.startDate()).isEqualTo(january);
    }

    @Test
    void aReviewedPeriodIsSkippedForTheNextOne() {
        LocalDate january = LocalDate.of(2027, 1, 20);
        Set<String> existing = Set.of("2026 Annual Review", "2027 Annual Review");

        CycleProposal next = ReviewCalendarService.propose(new ReviewCalendarSettings(), january, (p, n) -> existing.contains(n));

        assertThat(next.name()).isEqualTo("2028 Annual Review");
        assertThat(next.reviewPeriodStart()).isEqualTo(LocalDate.of(2028, 1, 1));
    }

    @Test
    void aPeriodLongPastIsNotProposed() {
        CycleProposal next = ReviewCalendarService.propose(new ReviewCalendarSettings(), LocalDate.of(2027, 4, 1), (p, n) -> false);

        assertThat(next.name()).isEqualTo("2027 Annual Review");
    }

    @Test
    void semiAnnualHalvesFollowAFiscalYear() {
        ReviewCalendarSettings settings = new ReviewCalendarSettings();
        settings.setCadence(ReviewCadence.SEMI_ANNUAL);
        settings.setYearStartMonth(4);

        CycleProposal mid = ReviewCalendarService.propose(settings, LocalDate.of(2026, 6, 1), (p, n) -> false);
        assertThat(mid.name()).isEqualTo("2026/27 Mid-year Review");
        assertThat(mid.reviewPeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(mid.reviewPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));

        CycleProposal end = ReviewCalendarService.propose(settings, MID_OCTOBER, (p, n) -> n.equals(mid.name()));
        assertThat(end.name()).isEqualTo("2026/27 Year-end Review");
        assertThat(end.reviewPeriodEnd()).isEqualTo(LocalDate.of(2027, 3, 31));

        CycleProposal previousYear = ReviewCalendarService.propose(settings, LocalDate.of(2026, 2, 1), (p, n) -> false);
        assertThat(previousYear.name()).isEqualTo("2025/26 Year-end Review");
        assertThat(previousYear.reviewPeriodStart()).isEqualTo(LocalDate.of(2025, 10, 1));
    }
}
