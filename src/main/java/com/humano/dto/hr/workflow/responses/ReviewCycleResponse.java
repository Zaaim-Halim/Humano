package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.FeedbackVisibility;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for review cycle details. {@code previousPhase} is the phase going back would
 * reopen: filled in for a single cycle only, and {@code null} in lists or when the cycle can't go back.
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
    ReviewCyclePhase previousPhase,
    PhaseDeadlines deadlines,
    CycleProgress progress,
    FeedbackSettings feedback,
    boolean requiresSignOff,
    ReviewCycleType type,
    Set<UUID> departmentIds,
    List<CycleEmployee> employees,
    CycleTemplate template,
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

    /**
     * The cycle's progress. The per-department breakdown, the feedback counts (requests sent,
     * feedback received) and the acknowledgement counts (reviews acknowledged, disputes waiting for
     * HR) are only filled in for a single cycle, and are empty / {@code null} in lists.
     */
    public record CycleProgress(
        int totalEmployees,
        int completedSelfAssessments,
        int completedManagerReviews,
        int deliveredFeedbacks,
        int completionPercentage,
        List<DepartmentProgress> departmentProgress,
        Integer feedbackRequested,
        Integer feedbackReceived,
        Integer acknowledged,
        Integer disputesOpen,
        Integer signOffPending,
        Integer signOffReturned
    ) {}

    /** Additional feedback: who sees it, whether employees nominate contributors, and how many a review may have. */
    /** An employee an ad hoc cycle reviews. */
    public record CycleEmployee(UUID id, String name) {}

    /** The review template the cycle uses; {@code null} for the standard form. */
    public record CycleTemplate(UUID id, String name) {}

    public record FeedbackSettings(FeedbackVisibility visibility, boolean allowNominations, int maxContributors) {}

    public record DepartmentProgress(
        UUID departmentId,
        String departmentName,
        int totalEmployees,
        int completedReviews,
        int completionPercentage
    ) {}
}
