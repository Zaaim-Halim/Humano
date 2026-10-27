package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why a goal is no longer pursued.
 */
public record CancelGoalRequest(@NotBlank(message = "Say why the goal is cancelled") @Size(max = 500) String reason) {}
