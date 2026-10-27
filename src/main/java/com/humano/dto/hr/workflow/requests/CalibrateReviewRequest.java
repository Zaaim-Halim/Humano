package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ReviewPotential;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Calibration of a review's final rating; the reviewer's own rating is kept alongside it. A
 * {@code potential} replaces the review's; without one it stays as it is.
 */
public record CalibrateReviewRequest(
    @NotNull(message = "Rating is required") @Min(1) @Max(5) Integer rating,

    @Size(max = 1000) String note,

    ReviewPotential potential
) {
    public CalibrateReviewRequest(Integer rating, String note) {
        this(rating, note, null);
    }
}
