package com.humano.dto.hr.workflow.requests;

import com.humano.domain.hr.ReviewRetentionSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * How long review records are kept: whole years after a cycle's review period ends, and whether the
 * daily job deletes cycles that are due.
 */
public record ReviewRetentionSettingsRequest(
    boolean enabled,
    @Min(ReviewRetentionSettings.MIN_RETAIN_YEARS) @Max(ReviewRetentionSettings.MAX_RETAIN_YEARS) int retainYears
) {}
