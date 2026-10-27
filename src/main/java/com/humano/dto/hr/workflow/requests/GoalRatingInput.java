package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.GoalAchievement;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The reviewer's view of how far one of the review's goals was achieved, with an optional comment.
 */
public record GoalRatingInput(
    @NotNull UUID goalId,

    @NotNull(message = "Rate every goal") GoalAchievement achievement,

    @Size(max = 2000) String comment
) {}
