package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a workflow instance tracking the state of a business process.
 * This entity serves as the central tracking mechanism for all workflow types
 * in the HR system including onboarding, approvals, and reviews.
 */
@Entity
@Table(
    name = "workflow_instance",
    indexes = {
        @Index(name = "idx_workflow_entity", columnList = "entity_id, entity_type"),
        @Index(name = "idx_workflow_type_status", columnList = "workflow_type, status"),
        @Index(name = "idx_workflow_assignee", columnList = "current_assignee_id"),
    }
)
public class WorkflowInstance extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Type of workflow (ONBOARDING, LEAVE_APPROVAL, etc.).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false, length = 50)
    private WorkflowType workflowType;

    /**
     * ID of the entity this workflow is associated with.
     */
    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Type of entity (e.g., "Employee", "LeaveRequest", "ExpenseClaim").
     */
    @NotNull
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * Current status of the workflow.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    /**
     * Current state within the workflow (workflow-specific state name).
     */
    @Column(name = "current_state", length = 50)
    private String currentState;

    /**
     * Current assignee responsible for the workflow.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assignee_id")
    private Employee currentAssignee;

    /**
     * Initiator of the workflow.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id")
    private Employee initiator;

    /**
     * Context data stored as JSON for workflow-specific information.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "jsonb")
    private Map<String, Object> context = new HashMap<>();

    /**
     * Timestamp when the workflow was started.
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Timestamp when the workflow was completed.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Due date for the workflow completion.
     */
    @Column(name = "due_date")
    private Instant dueDate;

    /**
     * Outcome of the workflow (e.g., "APPROVED", "REJECTED", "COMPLETED").
     */
    @Column(name = "outcome", length = 30)
    private String outcome;

    /**
     * Error message if the workflow failed.
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * Priority level (1-5, where 1 is highest priority).
     */
    @Column(name = "priority")
    private Integer priority = 3;

    /**
     * State transitions for audit trail.
     */
    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("transitionedAt ASC")
    private List<WorkflowStateTransition> transitions = new ArrayList<>();

    /**
     * Add a state transition to the workflow.
     */
    public WorkflowStateTransition addTransition(String fromState, String toState, String reason, String transitionedBy) {
        WorkflowStateTransition transition = new WorkflowStateTransition();
        transition.setWorkflowInstance(this);
        transition.setFromState(fromState);
        transition.setToState(toState);
        transition.setReason(reason);
        transition.setTransitionedBy(transitionedBy);
        transition.setTransitionedAt(Instant.now());
        this.transitions.add(transition);
        this.currentState = toState;
        return transition;
    }

    /**
     * Start the workflow.
     */
    public void start() {
        this.status = WorkflowStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    /**
     * Complete the workflow successfully.
     */
    public void complete(String outcome) {
        this.status = WorkflowStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.outcome = outcome;
    }

    /**
     * Fail the workflow with an error.
     */
    public void fail(String errorMessage) {
        this.status = WorkflowStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
        this.outcome = "FAILED";
    }

    /**
     * Cancel the workflow.
     */
    public void cancel(String reason) {
        this.status = WorkflowStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.outcome = "CANCELLED";
        this.errorMessage = reason;
    }

    /**
     * Add or update context data.
     */
    public void addContext(String key, Object value) {
        if (this.context == null) {
            this.context = new HashMap<>();
        }
        this.context.put(key, value);
    }

    /**
     * Get context value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        if (this.context == null) {
            return null;
        }
        return (T) this.context.get(key);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkflowType getWorkflowType() {
        return workflowType;
    }

    public void setWorkflowType(WorkflowType workflowType) {
        this.workflowType = workflowType;
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

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public Employee getCurrentAssignee() {
        return currentAssignee;
    }

    public void setCurrentAssignee(Employee currentAssignee) {
        this.currentAssignee = currentAssignee;
    }

    public Employee getInitiator() {
        return initiator;
    }

    public void setInitiator(Employee initiator) {
        this.initiator = initiator;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public List<WorkflowStateTransition> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<WorkflowStateTransition> transitions) {
        this.transitions = transitions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowInstance that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "WorkflowInstance{" +
            "id=" +
            id +
            ", workflowType=" +
            workflowType +
            ", entityId=" +
            entityId +
            ", entityType='" +
            entityType +
            '\'' +
            ", status=" +
            status +
            ", currentState='" +
            currentState +
            '\'' +
            '}'
        );
    }
}
