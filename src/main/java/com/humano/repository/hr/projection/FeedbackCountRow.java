package com.humano.repository.hr.projection;

import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import java.util.UUID;

/**
 * How many feedback requests of one review are in one status.
 */
public record FeedbackCountRow(UUID reviewId, FeedbackRequestStatus status, Long count) {}
