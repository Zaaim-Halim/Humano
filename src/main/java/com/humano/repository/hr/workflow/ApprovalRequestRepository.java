package com.humano.repository.hr.workflow;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.hr.ApprovalRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ApprovalRequest entity.
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID>, JpaSpecificationExecutor<ApprovalRequest> {
    /**
     * Find by entity ID and type.
     */
    Optional<ApprovalRequest> findByEntityIdAndApprovalType(UUID entityId, ApprovalType approvalType);

    /**
     * Find pending approvals for an approver.
     */
    Page<ApprovalRequest> findByApproverIdAndStatus(UUID approverId, WorkflowStatus status, Pageable pageable);

    /**
     * Find all pending approvals for an approver.
     */
    List<ApprovalRequest> findByApproverIdAndStatus(UUID approverId, WorkflowStatus status);

    /**
     * Find approvals by requestor.
     */
    Page<ApprovalRequest> findByRequestorId(UUID requestorId, Pageable pageable);

    /**
     * Find approvals by status.
     */
    Page<ApprovalRequest> findByStatus(WorkflowStatus status, Pageable pageable);

    /**
     * Find approvals by type and status.
     */
    Page<ApprovalRequest> findByApprovalTypeAndStatus(ApprovalType approvalType, WorkflowStatus status, Pageable pageable);

    /**
     * Find overdue approvals.
     */
    @Query("SELECT a FROM ApprovalRequest a WHERE a.dueDate < :now AND a.status = 'PENDING_APPROVAL'")
    List<ApprovalRequest> findOverdueApprovals(@Param("now") Instant now);

    /**
     * Count pending approvals for an approver.
     */
    long countByApproverIdAndStatus(UUID approverId, WorkflowStatus status);

    /**
     * Count pending approvals by type.
     */
    long countByApprovalTypeAndStatus(ApprovalType approvalType, WorkflowStatus status);

    /**
     * Find by workflow instance.
     */
    Optional<ApprovalRequest> findByWorkflowInstanceId(UUID workflowInstanceId);

    /**
     * Check if pending approval exists for entity.
     */
    @Query(
        "SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM ApprovalRequest a " +
        "WHERE a.entityId = :entityId AND a.approvalType = :type AND a.status = 'PENDING_APPROVAL'"
    )
    boolean existsPendingApproval(@Param("entityId") UUID entityId, @Param("type") ApprovalType type);

    /**
     * Find approvals submitted within date range.
     */
    Page<ApprovalRequest> findByApprovalTypeAndSubmittedAtBetween(
        ApprovalType approvalType,
        Instant startDate,
        Instant endDate,
        Pageable pageable
    );
}
