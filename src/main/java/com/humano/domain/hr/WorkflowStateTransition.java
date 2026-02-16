package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a state transition within a workflow.
 * Tracks the history of state changes for audit purposes.
 */
@Entity
@Table(
    name = "workflow_state_transition",
    indexes = {
        @Index(name = "idx_transition_workflow", columnList = "workflow_id"),
        @Index(name = "idx_transition_date", columnList = "transitioned_at"),
    }
)
public class WorkflowStateTransition extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "from_state", length = 50)
    private String fromState;

    @NotNull
    @Column(name = "to_state", nullable = false, length = 50)
    private String toState;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "transitioned_by", length = 50)
    private String transitionedBy;

    @NotNull
    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    // Constructors
    public WorkflowStateTransition() {}

    public WorkflowStateTransition(WorkflowInstance workflowInstance, String fromState, String toState, String reason) {
        this.workflowInstance = workflowInstance;
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
        this.transitionedAt = Instant.now();
    }

    // Getters and Setters
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

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public String getToState() {
        return toState;
    }

    public void setToState(String toState) {
        this.toState = toState;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTransitionedBy() {
        return transitionedBy;
    }

    public void setTransitionedBy(String transitionedBy) {
        this.transitionedBy = transitionedBy;
    }

    public Instant getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(Instant transitionedAt) {
        this.transitionedAt = transitionedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowStateTransition)) return false;
        return id != null && id.equals(((WorkflowStateTransition) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "WorkflowStateTransition{" +
            "id=" +
            id +
            ", fromState='" +
            fromState +
            '\'' +
            ", toState='" +
            toState +
            '\'' +
            ", transitionedAt=" +
            transitionedAt +
            '}'
        );
    }
}
