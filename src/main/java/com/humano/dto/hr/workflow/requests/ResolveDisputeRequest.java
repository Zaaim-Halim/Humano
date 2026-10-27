package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HR's answer to a disputed review, shared with the employee and the reviewer. A {@code rating}
 * different from the final one changes it, and the change is logged.
 */
public record ResolveDisputeRequest(
    @NotBlank(message = "Explain the outcome") @Size(max = 10000) String resolution,

    @Min(1) @Max(5) Integer rating
) {}
