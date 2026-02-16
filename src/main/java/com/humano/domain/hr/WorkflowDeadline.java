package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a deadline associated with a workflow.
 * Used for tracking and escalating overdue items.
 */
@Entity
@Table(
    name = "workflow_deadline",
    indexes = {
        @Index(name = "idx_deadline_workflow", columnList = "workflow_id"),
        @Index(name = "idx_deadline_date", columnList = "deadline_at, is_completed"),
    }
)
public class WorkflowDeadline extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The workflow instance this deadline belongs to.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowInstance workflowInstance;

    /**
     * Type of deadline (e.g., "APPROVAL", "TASK_COMPLETION", "REVIEW").
     */
    @NotNull
    @Column(name = "deadline_type", nullable = false, length = 50)
    private String deadlineType;

    /**
     * Description of the deadline.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * The deadline timestamp.
     */
    @NotNull
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    /**
     * Warning timestamp (when to send warning notification).
     */
    @Column(name = "warning_at")
    private Instant warningAt;

    /**
     * Current escalation level (0 = no escalation).
     */
    @Column(name = "escalation_level")
    private Integer escalationLevel = 0;

    /**
     * Whether the deadline has been met.
     */
    @NotNull
    @Column(name = "is_completed", nullable = false)
    private Boolean completed = false;

    /**
     * Timestamp when the deadline was completed.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Employee responsible for meeting this deadline.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Employee assignee;

    /**
     * Whether a warning notification has been sent.
     */
    @Column(name = "warning_sent")
    private Boolean warningSent = false;

    /**
     * Whether an overdue notification has been sent.
     */
    @Column(name = "overdue_sent")
    private Boolean overdueSent = false;

    /**
     * Mark the deadline as completed.
     */
    public void complete() {
        this.completed = true;
        this.completedAt = Instant.now();
    }

    /**
     * Escalate the deadline.
     */
    public void escalate() {
        this.escalationLevel++;
    }

    /**
     * Check if the deadline is overdue.
     */
    @Transient
    public boolean isOverdue() {
        return !completed && Instant.now().isAfter(deadlineAt);
    }

    /**
     * Check if warning should be sent.
     */
    @Transient
    public boolean shouldSendWarning() {
        return !completed && !warningSent && warningAt != null && Instant.now().isAfter(warningAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public void setWorkflowInstance(WorkflowInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    public String getDeadlineType() {
        return deadlineType;
    }

    public void setDeadlineType(String deadlineType) {
        this.deadlineType = deadlineType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Instant deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public Instant getWarningAt() {
        return warningAt;
    }

    public void setWarningAt(Instant warningAt) {
        this.warningAt = warningAt;
    }

    public Integer getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(Integer escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Employee getAssignee() {
        return assignee;
    }

    public void setAssignee(Employee assignee) {
        this.assignee = assignee;
    }

    public Boolean getWarningSent() {
        return warningSent;
    }

    public void setWarningSent(Boolean warningSent) {
        this.warningSent = warningSent;
    }

    public Boolean getOverdueSent() {
        return overdueSent;
    }

    public void setOverdueSent(Boolean overdueSent) {
        this.overdueSent = overdueSent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowDeadline that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "WorkflowDeadline{" +
            "id=" +
            id +
            ", deadlineType='" +
            deadlineType +
            '\'' +
            ", deadlineAt=" +
            deadlineAt +
            ", completed=" +
            completed +
            ", escalationLevel=" +
            escalationLevel +
            '}'
        );
    }
}
