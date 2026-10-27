package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.FeedbackRelationship;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ask someone for feedback on a review (the reviewer or HR), or nominate them (the reviewed
 * employee). Without a due date, the feedback is due a few days before the manager reviews are.
 */
public record RequestFeedbackRequest(
    @NotNull(message = "Choose who to ask") UUID contributorId,

    @NotNull(message = "Choose how they work together") FeedbackRelationship relationship,

    LocalDate dueDate,

    @Size(max = 1000) String message
) {}
