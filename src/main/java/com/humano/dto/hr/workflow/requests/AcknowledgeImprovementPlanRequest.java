package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Size;

/**
 * The employee confirms they received their improvement plan, optionally with a comment. This
 * records receipt, not agreement.
 */
public record AcknowledgeImprovementPlanRequest(@Size(max = 2000) String comment) {}
