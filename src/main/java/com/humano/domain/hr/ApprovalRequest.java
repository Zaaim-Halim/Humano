package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single approval request within an approval workflow.
 * Tracks the approval status at each level of the approval chain.
 */
@Entity
@Table(
    name = "approval_request",
    indexes = {
        @Index(name = "idx_approval_workflow", columnList = "workflow_id"),
        @Index(name = "idx_approval_approver", columnList = "approver_id, status"),
        @Index(name = "idx_approval_entity", columnList = "entity_id, entity_type"),
    }
)
public class ApprovalRequest extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The workflow instance this approval belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowInstance workflowInstance;

    /**
     * Type of approval.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 50)
    private ApprovalType approvalType;

    /**
     * ID of the entity being approved.
     */
    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Type of entity being approved.
     */
    @NotNull
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * Employee who submitted the request.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestor_id", nullable = false)
    private Employee requestor;

    /**
     * Current approver assigned to this request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Employee approver;

    /**
     * Current approval level in the chain.
     */
    @Column(name = "current_level")
    private Integer currentLevel = 1;

    /**
     * Status of this approval request.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkflowStatus status = WorkflowStatus.PENDING_APPROVAL;

    /**
     * Comments from the approver.
     */
    @Column(name = "approver_comments", length = 1000)
    private String approverComments;

    /**
     * Timestamp when the request was submitted.
     */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /**
     * Timestamp when a decision was made.
     */
    @Column(name = "decided_at")
    private Instant decidedAt;

    /**
     * Due date for the approval decision.
     */
    @Column(name = "due_date")
    private Instant dueDate;

    /**
     * Priority level (1-5).
     */
    @Column(name = "priority")
    private Integer priority = 3;

    /**
     * Amount associated with the request (for threshold-based approvals).
     */
    @Column(name = "amount")
    private Double amount;

    /**
     * Number of days associated with the request (e.g., leave days).
     */
    @Column(name = "days_count")
    private Integer daysCount;

    /**
     * Approve the request.
     */
    public void approve(String comments) {
        this.status = WorkflowStatus.APPROVED;
        this.approverComments = comments;
        this.decidedAt = Instant.now();
    }

    /**
     * Reject the request.
     */
    public void reject(String comments) {
        this.status = WorkflowStatus.REJECTED;
        this.approverComments = comments;
        this.decidedAt = Instant.now();
    }

    /**
     * Move to the next approval level.
     */
    public void moveToNextLevel(Employee nextApprover) {
        this.currentLevel++;
        this.approver = nextApprover;
        this.status = WorkflowStatus.PENDING_APPROVAL;
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

    public ApprovalType getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(ApprovalType approvalType) {
        this.approvalType = approvalType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Employee getRequestor() {
        return requestor;
    }

    public void setRequestor(Employee requestor) {
        this.requestor = requestor;
    }

    public Employee getApprover() {
        return approver;
    }

    public void setApprover(Employee approver) {
        this.approver = approver;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public String getApproverComments() {
        return approverComments;
    }

    public void setApproverComments(String approverComments) {
        this.approverComments = approverComments;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Integer getDaysCount() {
        return daysCount;
    }

    public void setDaysCount(Integer daysCount) {
        this.daysCount = daysCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApprovalRequest that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "ApprovalRequest{" +
            "id=" +
            id +
            ", approvalType=" +
            approvalType +
            ", entityId=" +
            entityId +
            ", status=" +
            status +
            ", currentLevel=" +
            currentLevel +
            '}'
        );
    }
}
