package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Performance analytics over the latest cycles of one type, newest first: counts only, so each rate
 * has its denominator at hand. Broken down by department alone — never by demographics.
 */
public record PerformanceAnalyticsResponse(ReviewCycleType type, List<CycleStats> cycles) {
    public record CycleStats(
        UUID cycleId,
        String name,
        ReviewCyclePhase phase,
        LocalDate reviewPeriodStart,
        LocalDate reviewPeriodEnd,
        Stats overall,
        List<DepartmentStats> departments
    ) {}

    /** {@code departmentId} is {@code null} for people without a department. */
    public record DepartmentStats(UUID departmentId, String name, Stats stats) {}

    /**
     * @param reviews              reviews in the cycle
     * @param selfSubmitted        with a submitted self-assessment (of {@code reviews})
     * @param managerSubmitted     written by the reviewer (of {@code reviews})
     * @param shared               shared with the employee (of {@code reviews})
     * @param acknowledged         acknowledged by the employee (of {@code shared})
     * @param disputed             acknowledged with a disagreement (of {@code shared})
     * @param rated                written, with a final rating — the base of the rest
     * @param distribution         how many got each final rating, 1 to 5 (of {@code rated})
     * @param averageRating        the average final rating, two decimals; empty with none rated
     * @param raised               final rating above the reviewer's (calibration or a dispute)
     * @param lowered              final rating below the reviewer's
     * @param promotionRecommended recommended for promotion by the reviewer (of {@code rated})
     * @param withImprovementPlan  an improvement plan was started from the review (of {@code rated})
     */
    public record Stats(
        int reviews,
        int selfSubmitted,
        int managerSubmitted,
        int shared,
        int acknowledged,
        int disputed,
        int rated,
        List<Integer> distribution,
        BigDecimal averageRating,
        int raised,
        int lowered,
        int promotionRecommended,
        int withImprovementPlan
    ) {}
}
