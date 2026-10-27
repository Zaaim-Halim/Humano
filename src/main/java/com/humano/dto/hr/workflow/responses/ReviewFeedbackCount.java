package com.humano.dto.hr.workflow.responses;

import java.util.UUID;

/**
 * Feedback requests of one review: nominations awaiting approval, requests sent (answered or not)
 * and feedback received.
 */
public record ReviewFeedbackCount(UUID reviewId, int awaitingApproval, int requested, int received) {}
