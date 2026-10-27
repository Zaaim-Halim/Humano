package com.humano.repository.hr.projection;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One cycle review, reduced to what performance analytics counts — no text, and of the employee only
 * their department ({@code null} without one).
 */
public record ReviewStatRow(
    UUID reviewId,
    UUID cycleId,
    UUID departmentId,
    String departmentName,
    PerformanceReviewStatus status,
    Instant selfSubmittedAt,
    Instant managerSubmittedAt,
    Instant acknowledgedAt,
    boolean disputed,
    Integer managerRating,
    Integer rating,
    boolean recommendForPromotion
) {}
