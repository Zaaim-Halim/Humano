package com.humano.repository.hr.workflow;

import com.humano.domain.hr.WorkflowStateTransition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for WorkflowStateTransition entity.
 */
@Repository
public interface WorkflowStateTransitionRepository extends JpaRepository<WorkflowStateTransition, UUID> {
    /**
     * Find all transitions for a workflow instance.
     */
    List<WorkflowStateTransition> findByWorkflowInstanceIdOrderByTransitionedAtAsc(UUID workflowInstanceId);

    /**
     * Find transitions within a date range.
     */
    List<WorkflowStateTransition> findByWorkflowInstanceIdAndTransitionedAtBetween(
        UUID workflowInstanceId,
        Instant startDate,
        Instant endDate
    );

    /**
     * Find the latest transition for a workflow.
     */
    WorkflowStateTransition findFirstByWorkflowInstanceIdOrderByTransitionedAtDesc(UUID workflowInstanceId);

    /**
     * Count transitions for a workflow.
     */
    long countByWorkflowInstanceId(UUID workflowInstanceId);
}
