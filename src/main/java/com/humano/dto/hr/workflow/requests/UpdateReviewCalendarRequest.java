package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ReviewCadence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The company's review calendar. Phase lengths are in days; a calibration length of 0 means the
 * company doesn't calibrate.
 */
public record UpdateReviewCalendarRequest(
    @NotNull ReviewCadence cadence,
    @Min(1) @Max(12) int yearStartMonth,
    @Min(0) @Max(90) int opensDaysBeforePeriodEnd,
    @Min(1) @Max(90) int selfAssessmentDays,
    @Min(1) @Max(90) int managerReviewDays,
    @Min(0) @Max(60) int calibrationDays,
    @Min(1) @Max(90) int feedbackDays
) {}
