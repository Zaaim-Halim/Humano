package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Assigns who reviews an employee in a cycle.
 */
public record AssignReviewerRequest(@NotNull(message = "Reviewer is required") UUID reviewerId) {}
