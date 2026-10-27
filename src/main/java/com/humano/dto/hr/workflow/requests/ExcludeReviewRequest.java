package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why a review is taken out of its cycle — long leave, for example. Kept on the review for HR.
 */
public record ExcludeReviewRequest(@NotBlank(message = "Say why the review is excluded") @Size(max = 500) String reason) {}
