package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.RatingChangeSource;
import java.time.Instant;
import java.util.UUID;

/**
 * A change to a review's final rating after the reviewer gave it.
 */
public record RatingChangeResponse(
    UUID id,
    Integer fromRating,
    Integer toRating,
    RatingChangeSource source,
    String reason,
    UUID changedById,
    String changedByName,
    Instant changedAt
) {}
