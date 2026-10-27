package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewCadence;
import java.time.LocalDate;

/**
 * The company's review calendar ({@code customized} is false while the defaults apply) and the
 * next cycle it proposes — the first review period without a cycle, with its dates and deadlines.
 */
public record ReviewCalendarResponse(
    ReviewCadence cadence,
    int yearStartMonth,
    int opensDaysBeforePeriodEnd,
    int selfAssessmentDays,
    int managerReviewDays,
    int calibrationDays,
    int feedbackDays,
    boolean customized,
    CycleProposal nextCycle
) {
    /** A cycle as the calendar would set it up; {@code calibrationDeadline} is null without calibration. */
    public record CycleProposal(
        String name,
        LocalDate reviewPeriodStart,
        LocalDate reviewPeriodEnd,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate selfAssessmentDeadline,
        LocalDate managerReviewDeadline,
        LocalDate calibrationDeadline,
        LocalDate feedbackDeadline
    ) {}
}
