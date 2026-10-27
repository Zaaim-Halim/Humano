package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.hr.ReviewCycle;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse.CycleStats;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse.Stats;
import com.humano.repository.hr.projection.ReviewStatRow;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Performance analytics: each count on its own base, overall and per department. */
class PerformanceAnalyticsServiceTest {

    private static final UUID FINANCE = UUID.randomUUID();
    private static final UUID SALES = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void countsCompletionDistributionMovementAndRatesOnTheirOwnBase() {
        ReviewStatRow notStarted = row(FINANCE, PerformanceReviewStatus.NOT_STARTED, false, false, null, null, false);
        ReviewStatRow selfOnly = row(FINANCE, PerformanceReviewStatus.SELF_SUBMITTED, true, false, null, null, false);
        ReviewStatRow written = row(SALES, PerformanceReviewStatus.MANAGER_SUBMITTED, true, true, 3, 3, true);
        ReviewStatRow raised = shared(row(SALES, PerformanceReviewStatus.SHARED, true, true, 3, 4, false), true, false);
        ReviewStatRow lowered = shared(row(null, PerformanceReviewStatus.SHARED, false, true, 3, 2, false), true, true);

        Stats s = PerformanceAnalyticsService.stats(List.of(notStarted, selfOnly, written, raised, lowered), Set.of(lowered.reviewId()));

        assertThat(s.reviews()).isEqualTo(5);
        assertThat(s.selfSubmitted()).isEqualTo(3);
        assertThat(s.managerSubmitted()).isEqualTo(3);
        assertThat(s.shared()).isEqualTo(2);
        assertThat(s.acknowledged()).isEqualTo(2);
        assertThat(s.disputed()).isEqualTo(1);
        assertThat(s.rated()).isEqualTo(3);
        assertThat(s.distribution()).containsExactly(0, 1, 1, 1, 0);
        assertThat(s.averageRating()).isEqualByComparingTo("3.00");
        assertThat(s.raised()).isEqualTo(1);
        assertThat(s.lowered()).isEqualTo(1);
        assertThat(s.promotionRecommended()).isEqualTo(1);
        assertThat(s.withImprovementPlan()).isEqualTo(1);
    }

    @Test
    void departmentsAreListedByNameWithPeopleWithoutOneLast() {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        List<ReviewStatRow> rows = List.of(
            row(null, PerformanceReviewStatus.SHARED, true, true, 3, 3, false),
            row(SALES, PerformanceReviewStatus.SHARED, true, true, 4, 4, false),
            row(FINANCE, PerformanceReviewStatus.SHARED, true, true, 2, 2, false)
        );

        CycleStats stats = PerformanceAnalyticsService.cycleStats(cycle, rows, Set.of());

        assertThat(stats.departments()).extracting(d -> d.name()).containsExactly("Finance", "Sales", null);
        assertThat(stats.overall().rated()).isEqualTo(3);
        assertThat(stats.departments().get(1).stats().distribution()).containsExactly(0, 0, 0, 1, 0);
        assertThat(PerformanceAnalyticsService.stats(List.of(), Set.of()).averageRating()).isNull();
    }

    private static ReviewStatRow row(
        UUID department,
        PerformanceReviewStatus status,
        boolean self,
        boolean manager,
        Integer managerRating,
        Integer rating,
        boolean promotion
    ) {
        return new ReviewStatRow(
            UUID.randomUUID(),
            UUID.randomUUID(),
            department,
            department == null ? null : department == FINANCE ? "Finance" : "Sales",
            status,
            self ? AT : null,
            manager ? AT : null,
            null,
            false,
            managerRating,
            rating,
            promotion
        );
    }

    private static ReviewStatRow shared(ReviewStatRow r, boolean acknowledged, boolean disputed) {
        return new ReviewStatRow(
            r.reviewId(),
            r.cycleId(),
            r.departmentId(),
            r.departmentName(),
            r.status(),
            r.selfSubmittedAt(),
            r.managerSubmittedAt(),
            acknowledged ? AT : null,
            disputed,
            r.managerRating(),
            r.rating(),
            r.recommendForPromotion()
        );
    }
}
