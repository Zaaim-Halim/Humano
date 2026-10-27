package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ProbationRecommendation;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.hr.ReviewPotential;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.dto.hr.workflow.responses.ReviewForm;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A performance review. {@code rating} and {@code comments} are the final rating and the reviewer's
 * comments; for a review written outside a cycle they are all there is. A cycle review adds the
 * employee's self-assessment and the reviewer's structured assessment — each {@code null} until
 * submitted, and the reviewer's side also while it is hidden from the employee reading it (until
 * the review is shared). {@code phaseDeadline} is the running cycle's deadline for its current phase,
 * {@code null} outside a phase with one. {@code acknowledgement} is {@code null} until the employee
 * acknowledges the shared review. {@code signOff} is the second-level sign-off of the written
 * review, in a cycle that requires one; never shown to the reviewed employee. {@code exclusion} says
 * why an {@code EXCLUDED} review was taken out of its cycle; it too is never shown to the employee.
 */
public record PerformanceReviewResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeJobTitle,
    String departmentName,
    UUID reviewerId,
    String reviewerName,
    LocalDate reviewDate,
    String comments,
    Integer rating,
    UUID cycleId,
    String cycleName,
    ReviewCycleType cycleType,
    ReviewCyclePhase cyclePhase,
    LocalDate phaseDeadline,
    PerformanceReviewStatus status,
    SelfAssessment selfAssessment,
    ManagerAssessment managerAssessment,
    String feedbackNotes,
    Instant sharedAt,
    Acknowledgement acknowledgement,
    SignOff signOff,
    Exclusion exclusion,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate,
    ReviewForm form
) {
    /**
     * This response with the template part of the review — only on single-review responses, so
     * lists stay light. {@code null} for the standard form.
     */
    public PerformanceReviewResponse withForm(ReviewForm form) {
        return new PerformanceReviewResponse(
            id,
            employeeId,
            employeeName,
            employeeJobTitle,
            departmentName,
            reviewerId,
            reviewerName,
            reviewDate,
            comments,
            rating,
            cycleId,
            cycleName,
            cycleType,
            cyclePhase,
            phaseDeadline,
            status,
            selfAssessment,
            managerAssessment,
            feedbackNotes,
            sharedAt,
            acknowledgement,
            signOff,
            exclusion,
            createdBy,
            createdDate,
            lastModifiedBy,
            lastModifiedDate,
            form
        );
    }

    /**
     * The employee's acknowledgement of the shared review (read, not necessarily agreed), with their
     * comment; when {@code disputed}, the comment is why, and HR's resolution follows.
     */
    public record Acknowledgement(
        Instant acknowledgedAt,
        String comment,
        boolean disputed,
        String resolution,
        Instant resolvedAt,
        String resolvedByName
    ) {}

    /** When and why the review was taken out of its cycle; {@code excludedByName} is empty when the employee left. */
    public record Exclusion(Instant excludedAt, String reason, String excludedByName) {}

    /** Where the written review stands in its sign-off, and the approver's last decision. */
    public record SignOff(SignOffStatus status, String comment, Instant decidedAt, String decidedByName) {}

    public record SelfAssessment(String achievements, String challenges, String developmentGoals, String comments, Instant submittedAt) {}

    /**
     * The reviewer's assessment. For the reviewed employee, {@code rating} (the reviewer's rating
     * before calibration), the promotion and probation recommendations, the calibration note and
     * the potential are {@code null}: they stay with HR and management.
     */
    public record ManagerAssessment(
        Integer rating,
        String strengths,
        String areasForImprovement,
        String developmentRecommendations,
        Boolean recommendForPromotion,
        String promotionJustification,
        ProbationRecommendation probationRecommendation,
        String calibrationNote,
        ReviewPotential potential,
        Instant submittedAt
    ) {}
}
