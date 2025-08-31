package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.enumeration.hr.OvertimeType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a record of overtime worked by an employee, including hours, type, approval status, and approver.
 * <p>
 * Used to track and manage overtime compensation and approvals.
 */
@Entity
@Table(name = "overtime_record")
public class OvertimeRecord extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Date the overtime was worked.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Number of overtime hours worked.
     */
    @Column(name = "hours", nullable = false)
    private BigDecimal hours;

    /**
     * Type of overtime (e.g., WEEKEND, HOLIDAY).
     */
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OvertimeType type;

    /**
     * Approval status of the overtime (e.g., PENDING, APPROVED).
     */
    @Column(name = "approval_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OvertimeApprovalStatus approvalStatus;

    /**
     * Optional notes about the overtime.
     */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * The employee who approved the overtime.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    /**
     * The employee who worked the overtime.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
