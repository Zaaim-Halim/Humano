package com.humano.repository.hr.workflow;

import com.humano.domain.hr.WorkflowDeadline;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for WorkflowDeadline entity.
 */
@Repository
public interface WorkflowDeadlineRepository extends JpaRepository<WorkflowDeadline, UUID> {
    /**
     * Find deadlines by workflow instance.
     */
    List<WorkflowDeadline> findByWorkflowInstanceId(UUID workflowInstanceId);

    /**
     * Find incomplete deadlines by workflow instance.
     */
    List<WorkflowDeadline> findByWorkflowInstanceIdAndCompletedFalse(UUID workflowInstanceId);

    /**
     * Find overdue deadlines.
     */
    @Query("SELECT d FROM WorkflowDeadline d WHERE d.deadlineAt < :now AND d.completed = false")
    List<WorkflowDeadline> findOverdueDeadlines(@Param("now") Instant now);

    /**
     * Find deadlines that need warning notification.
     */
    @Query("SELECT d FROM WorkflowDeadline d WHERE d.warningAt < :now AND d.completed = false AND d.warningSent = false")
    List<WorkflowDeadline> findDeadlinesNeedingWarning(@Param("now") Instant now);

    /**
     * Find deadlines by assignee.
     */
    List<WorkflowDeadline> findByAssigneeIdAndCompletedFalse(UUID assigneeId);

    /**
     * Find deadlines approaching within hours.
     */
    @Query("SELECT d FROM WorkflowDeadline d WHERE d.deadlineAt BETWEEN :now AND :deadline AND d.completed = false")
    List<WorkflowDeadline> findApproachingDeadlines(@Param("now") Instant now, @Param("deadline") Instant deadline);

    /**
     * Find deadlines by type.
     */
    List<WorkflowDeadline> findByDeadlineTypeAndCompletedFalse(String deadlineType);

    /**
     * Count overdue deadlines for workflow.
     */
    @Query(
        "SELECT COUNT(d) FROM WorkflowDeadline d WHERE d.workflowInstance.id = :workflowId AND d.deadlineAt < :now AND d.completed = false"
    )
    long countOverdueByWorkflowId(@Param("workflowId") UUID workflowId, @Param("now") Instant now);
}
