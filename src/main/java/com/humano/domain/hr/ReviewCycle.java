package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.FeedbackVisibility;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.*;

/**
 * Represents a performance review cycle that encompasses multiple employee reviews.
 * Tracks the phases and progress of the entire review process.
 */
@Entity
@Table(
    name = "review_cycle",
    indexes = {
        @Index(name = "idx_review_cycle_phase", columnList = "phase"),
        @Index(name = "idx_review_cycle_dates", columnList = "start_date, end_date"),
    }
)
public class ReviewCycle extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Name of the review cycle (e.g., "2026 Annual Review").
     */
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Description of the review cycle.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Review period start date.
     */
    @NotNull
    @Column(name = "review_period_start", nullable = false)
    private LocalDate reviewPeriodStart;

    /**
     * Review period end date.
     */
    @NotNull
    @Column(name = "review_period_end", nullable = false)
    private LocalDate reviewPeriodEnd;

    /**
     * Start date of the review cycle process.
     */
    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * End date of the review cycle process.
     */
    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Current phase of the review cycle.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 30)
    private ReviewCyclePhase phase = ReviewCyclePhase.DRAFT;

    /**
     * What the cycle is for: regular, probation or ad hoc — who it reviews, and whether it may
     * overlap other cycles.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 20)
    private ReviewCycleType type = ReviewCycleType.REGULAR;

    /**
     * Deadline for self-assessment phase.
     */
    @Column(name = "self_assessment_deadline")
    private LocalDate selfAssessmentDeadline;

    /**
     * Deadline for manager review phase.
     */
    @Column(name = "manager_review_deadline")
    private LocalDate managerReviewDeadline;

    /**
     * Deadline for calibration phase.
     */
    @Column(name = "calibration_deadline")
    private LocalDate calibrationDeadline;

    /**
     * Deadline for feedback delivery phase.
     */
    @Column(name = "feedback_deadline")
    private LocalDate feedbackDeadline;

    /**
     * Departments included in this review cycle.
     */
    @ElementCollection
    @CollectionTable(name = "review_cycle_departments", joinColumns = @JoinColumn(name = "cycle_id"))
    @Column(name = "department_id")
    private Set<UUID> departmentIds = new HashSet<>();

    /**
     * The employees an ad hoc cycle reviews; empty for the other types.
     */
    @ElementCollection
    @CollectionTable(name = "review_cycle_employees", joinColumns = @JoinColumn(name = "cycle_id"))
    @Column(name = "employee_id")
    private Set<UUID> employeeIds = new HashSet<>();

    /**
     * Employees in the cycle: those in scope while it is a draft, then the number of its reviews.
     */
    @Column(name = "total_employees")
    private Integer totalEmployees = 0;

    /**
     * Number of completed self-assessments (recounted from the reviews).
     */
    @Column(name = "completed_self_assessments")
    private Integer completedSelfAssessments = 0;

    /**
     * Number of completed manager reviews.
     */
    @Column(name = "completed_manager_reviews")
    private Integer completedManagerReviews = 0;

    /**
     * Number of delivered feedbacks.
     */
    @Column(name = "delivered_feedbacks")
    private Integer deliveredFeedbacks = 0;

    /**
     * Whether the cycle is active.
     */
    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    /**
     * Whether the reviewed employees see the feedback their contributors gave, once their review is shared.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_visibility", nullable = false, length = 30)
    private FeedbackVisibility feedbackVisibility = FeedbackVisibility.MANAGER_ONLY;

    /**
     * Whether employees may nominate their own contributors (the reviewer approves each nomination).
     */
    @Column(name = "allow_feedback_nominations", nullable = false)
    private boolean allowFeedbackNominations = true;

    /**
     * Most contributors on one review; 0 turns additional feedback off for the cycle.
     */
    @Column(name = "max_contributors", nullable = false)
    private int maxContributors = 8;

    /**
     * Whether each written review needs a second-level sign-off (the reviewer's manager, or the
     * PERFORMANCE_REVIEW approval chain) before it can be calibrated or shared.
     */
    @Column(name = "requires_sign_off", nullable = false)
    private boolean requiresSignOff;

    /**
     * What reviews ask beyond the standard form; empty for the standard form. Picked while the
     * cycle is a draft and copied when it opens, so a later edit to the template changes nothing here.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ReviewTemplate template;

    /** The template's name when the cycle copied it, so renaming the template later doesn't relabel the cycle. */
    @Column(name = "template_name", length = 100)
    private String templateName;

    /**
     * Associated workflow instance.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowInstance workflowInstance;

    /**
     * Share of the current phase's work done: self-assessments, then manager reviews (also during
     * calibration), then reviews shared with their employee.
     */
    @Transient
    public int getCompletionPercentage() {
        if (phase == ReviewCyclePhase.COMPLETED || phase == ReviewCyclePhase.ARCHIVED) {
            return 100;
        }
        if (totalEmployees == null || totalEmployees == 0) {
            return 0;
        }
        int done =
            switch (phase) {
                case DRAFT -> 0;
                case SELF_ASSESSMENT -> nullToZero(completedSelfAssessments);
                case MANAGER_REVIEW, CALIBRATION -> nullToZero(completedManagerReviews);
                case FEEDBACK_DELIVERY, GOAL_SETTING, COMPLETED, ARCHIVED -> nullToZero(deliveredFeedbacks);
            };
        return (done * 100) / totalEmployees;
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * Move to the next phase.
     */
    public void moveToNextPhase() {
        this.phase = switch (phase) {
            case DRAFT -> ReviewCyclePhase.SELF_ASSESSMENT;
            case SELF_ASSESSMENT -> ReviewCyclePhase.MANAGER_REVIEW;
            case MANAGER_REVIEW -> ReviewCyclePhase.CALIBRATION;
            case CALIBRATION -> ReviewCyclePhase.FEEDBACK_DELIVERY;
            case FEEDBACK_DELIVERY -> ReviewCyclePhase.GOAL_SETTING;
            case GOAL_SETTING -> ReviewCyclePhase.COMPLETED;
            case COMPLETED -> ReviewCyclePhase.ARCHIVED;
            case ARCHIVED -> ReviewCyclePhase.ARCHIVED;
        };
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getReviewPeriodStart() {
        return reviewPeriodStart;
    }

    public void setReviewPeriodStart(LocalDate reviewPeriodStart) {
        this.reviewPeriodStart = reviewPeriodStart;
    }

    public LocalDate getReviewPeriodEnd() {
        return reviewPeriodEnd;
    }

    public void setReviewPeriodEnd(LocalDate reviewPeriodEnd) {
        this.reviewPeriodEnd = reviewPeriodEnd;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public ReviewCyclePhase getPhase() {
        return phase;
    }

    public void setPhase(ReviewCyclePhase phase) {
        this.phase = phase;
    }

    public LocalDate getSelfAssessmentDeadline() {
        return selfAssessmentDeadline;
    }

    public void setSelfAssessmentDeadline(LocalDate selfAssessmentDeadline) {
        this.selfAssessmentDeadline = selfAssessmentDeadline;
    }

    public LocalDate getManagerReviewDeadline() {
        return managerReviewDeadline;
    }

    public void setManagerReviewDeadline(LocalDate managerReviewDeadline) {
        this.managerReviewDeadline = managerReviewDeadline;
    }

    public LocalDate getCalibrationDeadline() {
        return calibrationDeadline;
    }

    public void setCalibrationDeadline(LocalDate calibrationDeadline) {
        this.calibrationDeadline = calibrationDeadline;
    }

    public LocalDate getFeedbackDeadline() {
        return feedbackDeadline;
    }

    public void setFeedbackDeadline(LocalDate feedbackDeadline) {
        this.feedbackDeadline = feedbackDeadline;
    }

    public Set<UUID> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(Set<UUID> departmentIds) {
        this.departmentIds = departmentIds;
    }

    public Integer getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(Integer totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public Integer getCompletedSelfAssessments() {
        return completedSelfAssessments;
    }

    public void setCompletedSelfAssessments(Integer completedSelfAssessments) {
        this.completedSelfAssessments = completedSelfAssessments;
    }

    public Integer getCompletedManagerReviews() {
        return completedManagerReviews;
    }

    public void setCompletedManagerReviews(Integer completedManagerReviews) {
        this.completedManagerReviews = completedManagerReviews;
    }

    public Integer getDeliveredFeedbacks() {
        return deliveredFeedbacks;
    }

    public void setDeliveredFeedbacks(Integer deliveredFeedbacks) {
        this.deliveredFeedbacks = deliveredFeedbacks;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public FeedbackVisibility getFeedbackVisibility() {
        return feedbackVisibility;
    }

    public void setFeedbackVisibility(FeedbackVisibility feedbackVisibility) {
        this.feedbackVisibility = feedbackVisibility;
    }

    public boolean isAllowFeedbackNominations() {
        return allowFeedbackNominations;
    }

    public void setAllowFeedbackNominations(boolean allowFeedbackNominations) {
        this.allowFeedbackNominations = allowFeedbackNominations;
    }

    public int getMaxContributors() {
        return maxContributors;
    }

    public void setMaxContributors(int maxContributors) {
        this.maxContributors = maxContributors;
    }

    public ReviewCycleType getType() {
        return type;
    }

    public void setType(ReviewCycleType type) {
        this.type = type;
    }

    public Set<UUID> getEmployeeIds() {
        return employeeIds;
    }

    public void setEmployeeIds(Set<UUID> employeeIds) {
        this.employeeIds = employeeIds;
    }

    public boolean isRequiresSignOff() {
        return requiresSignOff;
    }

    public void setRequiresSignOff(boolean requiresSignOff) {
        this.requiresSignOff = requiresSignOff;
    }

    public ReviewTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ReviewTemplate template) {
        this.template = template;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public void setWorkflowInstance(WorkflowInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviewCycle that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "ReviewCycle{" +
            "id=" +
            id +
            ", name='" +
            name +
            '\'' +
            ", phase=" +
            phase +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            '}'
        );
    }
}
