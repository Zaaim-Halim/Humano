package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ProbationRecommendation;
import com.humano.domain.enumeration.hr.ReviewPotential;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A performance review of one employee.
 * <p>
 * Reviews are either written directly by HR (no {@link #cycle}, {@link PerformanceReviewStatus#SHARED}
 * from the start) or created by a {@link ReviewCycle} when its self-assessment phase starts. A cycle
 * review collects the employee's self-assessment, then the reviewer's assessment; {@link #rating} is
 * the final rating (the reviewer's, possibly adjusted in calibration). The employee sees the
 * reviewer's side only once the review is {@link PerformanceReviewStatus#SHARED}.
 */
@Entity
@Table(
    name = "performance_review",
    uniqueConstraints = @UniqueConstraint(name = "uk_performance_review_cycle_employee", columnNames = { "cycle_id", "employee_id" })
)
public class PerformanceReview extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Date the review was conducted.
     */
    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    /**
     * Comments provided during the review.
     */
    @Lob
    @Column(name = "comments")
    private String comments;

    /**
     * Final rating, 1–5: the reviewer's rating, or the calibrated one.
     */
    @Column(name = "rating")
    @Min(0)
    @Max(5)
    private Integer rating;

    /**
     * The employee who conducts the review. Empty on a cycle review when the employee had no
     * manager; HR assigns one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Employee reviewer;

    /**
     * The cycle this review belongs to; empty for a review written outside a cycle.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private ReviewCycle cycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PerformanceReviewStatus status = PerformanceReviewStatus.SHARED;

    @Lob
    @Column(name = "self_achievements")
    private String selfAchievements;

    @Lob
    @Column(name = "self_challenges")
    private String selfChallenges;

    @Lob
    @Column(name = "self_development_goals")
    private String selfDevelopmentGoals;

    @Lob
    @Column(name = "self_comments")
    private String selfComments;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    /**
     * The reviewer's own rating, kept when calibration changes {@link #rating}.
     */
    @Column(name = "manager_rating")
    @Min(1)
    @Max(5)
    private Integer managerRating;

    @Lob
    @Column(name = "strengths")
    private String strengths;

    @Lob
    @Column(name = "areas_for_improvement")
    private String areasForImprovement;

    @Lob
    @Column(name = "development_recommendations")
    private String developmentRecommendations;

    @Column(name = "recommend_for_promotion", nullable = false)
    private boolean recommendForPromotion;

    @Lob
    @Column(name = "promotion_justification")
    private String promotionJustification;

    /**
     * In a probation cycle, the reviewer's recommendation: confirm, extend or not confirm. A
     * recommendation only, never applied to the employee by itself, and never shown to them.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "probation_recommendation", length = 20)
    private ProbationRecommendation probationRecommendation;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    /**
     * Why calibration changed the reviewer's rating.
     */
    @Column(name = "calibration_note", length = 1000)
    private String calibrationNote;

    /**
     * The reviewer's view of the employee's potential, adjustable in calibration; with the rating,
     * it places the employee on the 9-box. Never shown to the employee.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "potential", length = 20)
    private ReviewPotential potential;

    /**
     * Notes from the feedback meeting.
     */
    @Lob
    @Column(name = "feedback_notes")
    private String feedbackNotes;

    @Column(name = "shared_at")
    private Instant sharedAt;

    /**
     * When the employee acknowledged the shared review — that they have read it, not that they
     * agree. Their comment and the meeting notes are locked from then on.
     */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** The employee's comment on the review, given when acknowledging it; the reason when they dispute it. */
    @Lob
    @Column(name = "employee_comment")
    private String employeeComment;

    /** The employee disagrees with the review and asked HR to look at it. */
    @Column(name = "disputed", nullable = false)
    private boolean disputed;

    /** HR's answer to the dispute, shared with the employee and the reviewer. */
    @Lob
    @Column(name = "dispute_resolution")
    private String disputeResolution;

    @Column(name = "dispute_resolved_at")
    private Instant disputeResolvedAt;

    /** Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_resolved_by_id")
    private Employee disputeResolvedBy;

    /** When the review was taken out of its cycle ({@code EXCLUDED}); empty otherwise. */
    @Column(name = "excluded_at")
    private Instant excludedAt;

    /** Why it was taken out: HR's reason, or that the employee left. */
    @Column(name = "exclusion_reason", length = 500)
    private String exclusionReason;

    /** Who took it out; empty when the employee's leaving did. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluded_by_id")
    private Employee excludedBy;

    /**
     * The second-level sign-off of the written review, in a cycle that requires one; empty
     * otherwise, and until the reviewer submits.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sign_off_status", length = 20)
    private SignOffStatus signOffStatus;

    /** The approver's comment: why it was returned, or a note with the sign-off. */
    @Column(name = "sign_off_comment", length = 2000)
    private String signOffComment;

    @Column(name = "sign_off_decided_at")
    private Instant signOffDecidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sign_off_decided_by_id")
    private Employee signOffDecidedBy;

    /**
     * The employee being reviewed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Getters and setters
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public PerformanceReview reviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
        return this;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public String getComments() {
        return comments;
    }

    public PerformanceReview comments(String comments) {
        this.comments = comments;
        return this;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Integer getRating() {
        return rating;
    }

    public PerformanceReview rating(Integer rating) {
        this.rating = rating;
        return this;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Employee getReviewer() {
        return reviewer;
    }

    public PerformanceReview reviewer(Employee reviewer) {
        this.reviewer = reviewer;
        return this;
    }

    public void setReviewer(Employee reviewer) {
        this.reviewer = reviewer;
    }

    public Employee getEmployee() {
        return employee;
    }

    public PerformanceReview employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public ReviewCycle getCycle() {
        return cycle;
    }

    public void setCycle(ReviewCycle cycle) {
        this.cycle = cycle;
    }

    public PerformanceReviewStatus getStatus() {
        return status;
    }

    public void setStatus(PerformanceReviewStatus status) {
        this.status = status;
    }

    public String getSelfAchievements() {
        return selfAchievements;
    }

    public void setSelfAchievements(String selfAchievements) {
        this.selfAchievements = selfAchievements;
    }

    public String getSelfChallenges() {
        return selfChallenges;
    }

    public void setSelfChallenges(String selfChallenges) {
        this.selfChallenges = selfChallenges;
    }

    public String getSelfDevelopmentGoals() {
        return selfDevelopmentGoals;
    }

    public void setSelfDevelopmentGoals(String selfDevelopmentGoals) {
        this.selfDevelopmentGoals = selfDevelopmentGoals;
    }

    public String getSelfComments() {
        return selfComments;
    }

    public void setSelfComments(String selfComments) {
        this.selfComments = selfComments;
    }

    public Instant getSelfSubmittedAt() {
        return selfSubmittedAt;
    }

    public void setSelfSubmittedAt(Instant selfSubmittedAt) {
        this.selfSubmittedAt = selfSubmittedAt;
    }

    public Integer getManagerRating() {
        return managerRating;
    }

    public void setManagerRating(Integer managerRating) {
        this.managerRating = managerRating;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getAreasForImprovement() {
        return areasForImprovement;
    }

    public void setAreasForImprovement(String areasForImprovement) {
        this.areasForImprovement = areasForImprovement;
    }

    public String getDevelopmentRecommendations() {
        return developmentRecommendations;
    }

    public void setDevelopmentRecommendations(String developmentRecommendations) {
        this.developmentRecommendations = developmentRecommendations;
    }

    public boolean isRecommendForPromotion() {
        return recommendForPromotion;
    }

    public void setRecommendForPromotion(boolean recommendForPromotion) {
        this.recommendForPromotion = recommendForPromotion;
    }

    public String getPromotionJustification() {
        return promotionJustification;
    }

    public void setPromotionJustification(String promotionJustification) {
        this.promotionJustification = promotionJustification;
    }

    public Instant getManagerSubmittedAt() {
        return managerSubmittedAt;
    }

    public void setManagerSubmittedAt(Instant managerSubmittedAt) {
        this.managerSubmittedAt = managerSubmittedAt;
    }

    public String getCalibrationNote() {
        return calibrationNote;
    }

    public void setCalibrationNote(String calibrationNote) {
        this.calibrationNote = calibrationNote;
    }

    public ProbationRecommendation getProbationRecommendation() {
        return probationRecommendation;
    }

    public void setProbationRecommendation(ProbationRecommendation probationRecommendation) {
        this.probationRecommendation = probationRecommendation;
    }

    public ReviewPotential getPotential() {
        return potential;
    }

    public void setPotential(ReviewPotential potential) {
        this.potential = potential;
    }

    public String getFeedbackNotes() {
        return feedbackNotes;
    }

    public void setFeedbackNotes(String feedbackNotes) {
        this.feedbackNotes = feedbackNotes;
    }

    public Instant getSharedAt() {
        return sharedAt;
    }

    public void setSharedAt(Instant sharedAt) {
        this.sharedAt = sharedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getEmployeeComment() {
        return employeeComment;
    }

    public void setEmployeeComment(String employeeComment) {
        this.employeeComment = employeeComment;
    }

    public boolean isDisputed() {
        return disputed;
    }

    public void setDisputed(boolean disputed) {
        this.disputed = disputed;
    }

    public String getDisputeResolution() {
        return disputeResolution;
    }

    public void setDisputeResolution(String disputeResolution) {
        this.disputeResolution = disputeResolution;
    }

    public Instant getDisputeResolvedAt() {
        return disputeResolvedAt;
    }

    public void setDisputeResolvedAt(Instant disputeResolvedAt) {
        this.disputeResolvedAt = disputeResolvedAt;
    }

    public Employee getDisputeResolvedBy() {
        return disputeResolvedBy;
    }

    public void setDisputeResolvedBy(Employee disputeResolvedBy) {
        this.disputeResolvedBy = disputeResolvedBy;
    }

    public Instant getExcludedAt() {
        return excludedAt;
    }

    public void setExcludedAt(Instant excludedAt) {
        this.excludedAt = excludedAt;
    }

    public String getExclusionReason() {
        return exclusionReason;
    }

    public void setExclusionReason(String exclusionReason) {
        this.exclusionReason = exclusionReason;
    }

    public Employee getExcludedBy() {
        return excludedBy;
    }

    public void setExcludedBy(Employee excludedBy) {
        this.excludedBy = excludedBy;
    }

    public SignOffStatus getSignOffStatus() {
        return signOffStatus;
    }

    public void setSignOffStatus(SignOffStatus signOffStatus) {
        this.signOffStatus = signOffStatus;
    }

    /** No sign-off needed, or it is signed off or waived. */
    public boolean isSignOffSettled() {
        return signOffStatus == null || signOffStatus.isSettled();
    }

    public String getSignOffComment() {
        return signOffComment;
    }

    public void setSignOffComment(String signOffComment) {
        this.signOffComment = signOffComment;
    }

    public Instant getSignOffDecidedAt() {
        return signOffDecidedAt;
    }

    public void setSignOffDecidedAt(Instant signOffDecidedAt) {
        this.signOffDecidedAt = signOffDecidedAt;
    }

    public Employee getSignOffDecidedBy() {
        return signOffDecidedBy;
    }

    public void setSignOffDecidedBy(Employee signOffDecidedBy) {
        this.signOffDecidedBy = signOffDecidedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PerformanceReview that = (PerformanceReview) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PerformanceReview{" + "id=" + id + ", reviewDate=" + reviewDate + ", status=" + status + ", rating=" + rating + '}';
    }
}
