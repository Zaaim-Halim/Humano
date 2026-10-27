package com.humano.dto.hr.workflow.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * A progress update on a goal.
 */
public record GoalCheckInResponse(UUID id, int progress, String comment, String authorName, Instant checkedInAt) {}
