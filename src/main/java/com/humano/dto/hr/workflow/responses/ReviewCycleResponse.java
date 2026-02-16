package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for review cycle details.
 */
public record ReviewCycleResponse(
    UUID cycleId,
    UUID workflowId,
    String name,
    String description,
    LocalDate reviewPeriodStart,
    LocalDate reviewPeriodEnd,
    LocalDate startDate,
    LocalDate endDate,
    ReviewCyclePhase phase,
    PhaseDeadlines deadlines,
    CycleProgress progress,
    Set<UUID> departmentIds,
    boolean active,
    Instant createdDate,
    Instant lastModifiedDate
) {
    public record PhaseDeadlines(
        LocalDate selfAssessmentDeadline,
        LocalDate managerReviewDeadline,
        LocalDate calibrationDeadline,
        LocalDate feedbackDeadline
    ) {}

    public record CycleProgress(
        int totalEmployees,
        int completedSelfAssessments,
        int completedManagerReviews,
        int deliveredFeedbacks,
        int completionPercentage,
        List<DepartmentProgress> departmentProgress
    ) {}

    public record DepartmentProgress(
        UUID departmentId,
        String departmentName,
        int totalEmployees,
        int completedReviews,
        int completionPercentage
    ) {}
}
