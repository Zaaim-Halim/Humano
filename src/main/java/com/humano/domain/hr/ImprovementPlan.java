package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.ImprovementPlanOutcome;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A performance improvement plan: started by the employee's manager or HR from a shared review rated
 * 2 or lower, it sets out what needs to improve, the objectives and how they are measured, the
 * support given and how often progress is reviewed, over a fixed period. The employee acknowledges it
 * (which records they received it, not that they agree), check-ins are recorded along the way, and it
 * ends with an outcome. Drafts are private to the manager and HR; the outcome never changes the
 * employee's status.
 */
@Entity
@Table(
    name = "improvement_plan",
    indexes = {
        @Index(name = "idx_improvement_plan_employee", columnList = "employee_id, status"),
        @Index(name = "idx_improvement_plan_status", columnList = "status"),
    }
)
public class ImprovementPlan extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    /** Who started the plan and runs it; empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private Employee owner;

    /** The shared review it was started from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImprovementPlanStatus status = ImprovementPlanStatus.DRAFT;

    /** What needs to improve, with examples. */
    @Lob
    @Column(name = "concerns", nullable = false)
    private String concerns;

    /** Training, coaching or resources provided. */
    @Lob
    @Column(name = "support")
    private String support;

    /** How often progress is reviewed, e.g. "Weekly one-to-one". */
    @Column(name = "meeting_cadence", length = 200)
    private String meetingCadence;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** The end date before the plan was extended; empty unless it was. */
    @Column(name = "original_end_date")
    private LocalDate originalEndDate;

    @Column(name = "extension_reason", length = 500)
    private String extensionReason;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "employee_comment", length = 2000)
    private String employeeComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private ImprovementPlanOutcome outcome;

    @Lob
    @Column(name = "outcome_note")
    private String outcomeNote;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private Employee closedBy;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ImprovementPlanObjective> objectives = new ArrayList<>();

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Employee getOwner() {
        return owner;
    }

    public void setOwner(Employee owner) {
        this.owner = owner;
    }

    public PerformanceReview getReview() {
        return review;
    }

    public void setReview(PerformanceReview review) {
        this.review = review;
    }

    public ImprovementPlanStatus getStatus() {
        return status;
    }

    public void setStatus(ImprovementPlanStatus status) {
        this.status = status;
    }

    public String getConcerns() {
        return concerns;
    }

    public void setConcerns(String concerns) {
        this.concerns = concerns;
    }

    public String getSupport() {
        return support;
    }

    public void setSupport(String support) {
        this.support = support;
    }

    public String getMeetingCadence() {
        return meetingCadence;
    }

    public void setMeetingCadence(String meetingCadence) {
        this.meetingCadence = meetingCadence;
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

    public LocalDate getOriginalEndDate() {
        return originalEndDate;
    }

    public void setOriginalEndDate(LocalDate originalEndDate) {
        this.originalEndDate = originalEndDate;
    }

    public String getExtensionReason() {
        return extensionReason;
    }

    public void setExtensionReason(String extensionReason) {
        this.extensionReason = extensionReason;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
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

    public ImprovementPlanOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ImprovementPlanOutcome outcome) {
        this.outcome = outcome;
    }

    public String getOutcomeNote() {
        return outcomeNote;
    }

    public void setOutcomeNote(String outcomeNote) {
        this.outcomeNote = outcomeNote;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Employee getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(Employee closedBy) {
        this.closedBy = closedBy;
    }

    public List<ImprovementPlanObjective> getObjectives() {
        return objectives;
    }

    public void setObjectives(List<ImprovementPlanObjective> objectives) {
        this.objectives = objectives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImprovementPlan that = (ImprovementPlan) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
