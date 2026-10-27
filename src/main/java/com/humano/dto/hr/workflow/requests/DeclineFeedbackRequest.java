package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Size;

/**
 * Why a contributor declines to give feedback, or why the reviewer turns a nomination down.
 */
public record DeclineFeedbackRequest(@Size(max = 500) String reason) {}
