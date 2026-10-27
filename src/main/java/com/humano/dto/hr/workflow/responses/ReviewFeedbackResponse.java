package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.FeedbackRelationship;
import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import com.humano.domain.enumeration.hr.FeedbackVisibility;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A request for feedback on a review, with the feedback once given. What a viewer may not see is
 * {@code null}: the feedback itself before it may be shown, and for an anonymous contribution
 * shown to the reviewed employee everything that could identify its author (the id included).
 *
 * @param visibility whether the reviewed employee will see the feedback once the review is shared
 *                   (the cycle's setting, so the contributor knows before answering)
 * @param open whether the contributor can still give or revise their feedback
 */
public record ReviewFeedbackResponse(
    UUID id,
    UUID reviewId,
    UUID cycleId,
    String cycleName,
    UUID employeeId,
    String employeeName,
    String employeeJobTitle,
    UUID contributorId,
    String contributorName,
    String contributorJobTitle,
    FeedbackRelationship relationship,
    FeedbackRequestStatus status,
    UUID requestedById,
    String requestedByName,
    Instant requestedAt,
    LocalDate dueDate,
    String message,
    String strengths,
    String improvements,
    String comments,
    Instant submittedAt,
    String declineReason,
    FeedbackVisibility visibility,
    boolean open
) {}
