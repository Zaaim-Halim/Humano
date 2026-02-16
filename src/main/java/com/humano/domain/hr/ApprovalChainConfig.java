package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.ApproverType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the configuration for an approval chain.
 * Defines the sequence and conditions for approvals.
 */
@Entity
@Table(name = "approval_chain_config", indexes = { @Index(name = "idx_approval_chain_type", columnList = "approval_type, sequence_order") })
public class ApprovalChainConfig extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Type of approval this configuration applies to.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 50)
    private ApprovalType approvalType;

    /**
     * Order in the approval sequence (1 = first approver).
     */
    @NotNull
    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    /**
     * Type of approver at this level.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 50)
    private ApproverType approverType;

    /**
     * Specific employee ID if approverType is SPECIFIC_EMPLOYEE.
     */
    @Column(name = "specific_approver_id")
    private UUID specificApproverId;

    /**
     * SpEL expression for conditional approval (e.g., "amount > 1000").
     */
    @Column(name = "condition_expression", length = 500)
    private String conditionExpression;

    /**
     * Description of when this approval level is required.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Whether this approval level is active.
     */
    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    /**
     * Minimum threshold value that triggers this approval level.
     */
    @Column(name = "min_threshold")
    private Double minThreshold;

    /**
     * Maximum threshold value for this approval level.
     */
    @Column(name = "max_threshold")
    private Double maxThreshold;

    /**
     * Department ID if this config is department-specific.
     */
    @Column(name = "department_id")
    private UUID departmentId;

    /**
     * Auto-approve after this many hours if no action taken.
     */
    @Column(name = "auto_approve_hours")
    private Integer autoApproveHours;

    /**
     * Auto-escalate after this many hours if no action taken.
     */
    @Column(name = "escalate_after_hours")
    private Integer escalateAfterHours;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ApprovalType getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(ApprovalType approvalType) {
        this.approvalType = approvalType;
    }

    public Integer getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public ApproverType getApproverType() {
        return approverType;
    }

    public void setApproverType(ApproverType approverType) {
        this.approverType = approverType;
    }

    public UUID getSpecificApproverId() {
        return specificApproverId;
    }

    public void setSpecificApproverId(UUID specificApproverId) {
        this.specificApproverId = specificApproverId;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Double getMinThreshold() {
        return minThreshold;
    }

    public void setMinThreshold(Double minThreshold) {
        this.minThreshold = minThreshold;
    }

    public Double getMaxThreshold() {
        return maxThreshold;
    }

    public void setMaxThreshold(Double maxThreshold) {
        this.maxThreshold = maxThreshold;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public Integer getAutoApproveHours() {
        return autoApproveHours;
    }

    public void setAutoApproveHours(Integer autoApproveHours) {
        this.autoApproveHours = autoApproveHours;
    }

    public Integer getEscalateAfterHours() {
        return escalateAfterHours;
    }

    public void setEscalateAfterHours(Integer escalateAfterHours) {
        this.escalateAfterHours = escalateAfterHours;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApprovalChainConfig that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "ApprovalChainConfig{" +
            "id=" +
            id +
            ", approvalType=" +
            approvalType +
            ", sequenceOrder=" +
            sequenceOrder +
            ", approverType=" +
            approverType +
            ", active=" +
            active +
            '}'
        );
    }
}
