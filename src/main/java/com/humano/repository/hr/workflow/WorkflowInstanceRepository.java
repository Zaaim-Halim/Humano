package com.humano.repository.hr.workflow;

import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.WorkflowInstance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for WorkflowInstance entity.
 */
@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    /**
     * Find workflows by entity.
     */
    List<WorkflowInstance> findByEntityIdAndEntityType(UUID entityId, String entityType);

    /**
     * Find workflows by entity ID.
     */
    List<WorkflowInstance> findByEntityId(UUID entityId);

    /**
     * Find active workflows by entity ID.
     */
    @Query("SELECT w FROM WorkflowInstance w WHERE w.entityId = :entityId AND w.status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED')")
    List<WorkflowInstance> findActiveWorkflowsByEntityId(@Param("entityId") UUID entityId);

    /**
     * Check if active workflow exists for entity.
     */
    @Query(
        "SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WorkflowInstance w WHERE w.entityId = :entityId AND w.workflowType = :type AND w.status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED')"
    )
    boolean existsActiveWorkflow(@Param("entityId") UUID entityId, @Param("type") WorkflowType type);

    /**
     * Find active workflow for an entity.
     */
    Optional<WorkflowInstance> findByEntityIdAndEntityTypeAndStatusNot(UUID entityId, String entityType, WorkflowStatus status);

    /**
     * Find workflows by type and status.
     */
    Page<WorkflowInstance> findByWorkflowTypeAndStatus(WorkflowType type, WorkflowStatus status, Pageable pageable);

    /**
     * Find workflows by type.
     */
    Page<WorkflowInstance> findByWorkflowType(WorkflowType type, Pageable pageable);

    /**
     * Find workflows by current assignee.
     */
    Page<WorkflowInstance> findByCurrentAssigneeIdAndStatusIn(UUID assigneeId, List<WorkflowStatus> statuses, Pageable pageable);

    /**
     * Find workflows by current assignee.
     */
    Page<WorkflowInstance> findByCurrentAssigneeId(UUID assigneeId, Pageable pageable);

    /**
     * Find workflows by initiator.
     */
    Page<WorkflowInstance> findByInitiatorId(UUID initiatorId, Pageable pageable);

    /**
     * Find workflows by status.
     */
    List<WorkflowInstance> findByStatus(WorkflowStatus status);

    /**
     * Find overdue workflows.
     */
    @Query("SELECT w FROM WorkflowInstance w WHERE w.dueDate < :now AND w.status IN :activeStatuses")
    List<WorkflowInstance> findOverdueWorkflows(@Param("now") Instant now, @Param("activeStatuses") List<WorkflowStatus> activeStatuses);

    /**
     * Find overdue workflows (simplified).
     */
    @Query("SELECT w FROM WorkflowInstance w WHERE w.dueDate < :now AND w.status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED')")
    List<WorkflowInstance> findOverdueWorkflows(@Param("now") Instant now);

    /**
     * Count workflows by type and status.
     */
    long countByWorkflowTypeAndStatus(WorkflowType type, WorkflowStatus status);

    /**
     * Find workflows created in date range.
     */
    @Query("SELECT w FROM WorkflowInstance w WHERE w.createdDate BETWEEN :startDate AND :endDate")
    Page<WorkflowInstance> findByCreatedDateBetween(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );
}
