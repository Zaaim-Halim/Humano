package com.humano.repository.hr.workflow;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.hr.ApprovalChainConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ApprovalChainConfig entity.
 */
@Repository
public interface ApprovalChainConfigRepository extends JpaRepository<ApprovalChainConfig, UUID> {
    /**
     * Find all active configurations for an approval type, ordered by sequence.
     */
    List<ApprovalChainConfig> findByApprovalTypeAndActiveTrueOrderBySequenceOrderAsc(ApprovalType approvalType);

    /**
     * Find configuration by approval type and sequence order.
     */
    Optional<ApprovalChainConfig> findByApprovalTypeAndSequenceOrderAndActiveTrue(ApprovalType approvalType, Integer sequenceOrder);

    /**
     * Find configurations for a specific department.
     */
    List<ApprovalChainConfig> findByApprovalTypeAndDepartmentIdAndActiveTrueOrderBySequenceOrderAsc(
        ApprovalType approvalType,
        UUID departmentId
    );

    /**
     * Find configurations by threshold range.
     */
    @Query(
        "SELECT c FROM ApprovalChainConfig c WHERE c.approvalType = :type AND c.active = true " +
        "AND (c.minThreshold IS NULL OR c.minThreshold <= :amount) " +
        "AND (c.maxThreshold IS NULL OR c.maxThreshold >= :amount) " +
        "ORDER BY c.sequenceOrder ASC"
    )
    List<ApprovalChainConfig> findByApprovalTypeAndAmountThreshold(@Param("type") ApprovalType type, @Param("amount") Double amount);

    /**
     * Get the maximum sequence order for an approval type.
     */
    @Query("SELECT MAX(c.sequenceOrder) FROM ApprovalChainConfig c WHERE c.approvalType = :type AND c.active = true")
    Integer findMaxSequenceOrder(@Param("type") ApprovalType type);

    /**
     * Check if configuration exists for approval type.
     */
    boolean existsByApprovalTypeAndActiveTrue(ApprovalType approvalType);
}
