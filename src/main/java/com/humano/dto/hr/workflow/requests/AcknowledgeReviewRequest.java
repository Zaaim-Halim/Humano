package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Size;

/**
 * The employee acknowledges their shared review: they have read it, which isn't agreeing with it.
 * With {@code disputed}, they disagree and ask HR to look at it; the comment then says why.
 */
public record AcknowledgeReviewRequest(@Size(max = 10000) String comment, boolean disputed) {}
