package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ImprovementPlanOutcome;
import com.humano.domain.enumeration.hr.ImprovementPlanProgress;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An improvement plan as its page shows it, with what the signed-in user may do with it ({@code can}).
 * Lists leave {@code objectives} and {@code checkIns} empty.
 */
public record ImprovementPlanResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeJobTitle,
    String ownerName,
    UUID reviewId,
    String cycleName,
    /** The review's final rating, which the plan was started from. */
    Integer reviewRating,
    ImprovementPlanStatus status,
    String concerns,
    String support,
    String meetingCadence,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate originalEndDate,
    String extensionReason,
    Instant activatedAt,
    Instant acknowledgedAt,
    String employeeComment,
    ImprovementPlanOutcome outcome,
    String outcomeNote,
    Instant closedAt,
    String closedByName,
    List<Objective> objectives,
    List<CheckIn> checkIns,
    Actions can
) {
    public record Objective(UUID id, String description, String successMeasure, LocalDate dueDate) {}

    public record CheckIn(UUID id, ImprovementPlanProgress progress, String note, String authorName, Instant checkedInAt) {}

    /** What the signed-in user may do with the plan. */
    public record Actions(
        boolean edit,
        boolean activate,
        boolean delete,
        boolean checkIn,
        boolean extend,
        boolean close,
        boolean acknowledge
    ) {}
}
