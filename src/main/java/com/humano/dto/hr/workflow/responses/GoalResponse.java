package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.GoalStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal, with what the signed-in user may do with it. {@code locked}: rated in a submitted review,
 * so it can no longer change.
 */
public record GoalResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String title,
    String description,
    String successMeasure,
    Integer weight,
    LocalDate startDate,
    LocalDate dueDate,
    GoalStatus status,
    int progress,
    Instant agreedAt,
    String agreedByName,
    Instant completedAt,
    String cancelReason,
    boolean locked,
    Actions can
) {
    public record Actions(boolean edit, boolean agree, boolean checkIn, boolean complete, boolean cancel, boolean delete) {}
}
