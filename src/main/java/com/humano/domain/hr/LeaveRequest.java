package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a leave request made by an employee, including type, status, and date range.
 * <p>
 * Used to manage employee leave applications and approvals.
 */
@Entity
@Table(name = "leave_request")
public class LeaveRequest extends AbstractAuditingEntity<UUID> {
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
     * Start date of the leave.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * End date of the leave.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Type of leave (e.g., SICK, VACATION, UNPAID).
     */
    @Column(name = "leave_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    /**
     * Status of the leave request (e.g., PENDING, APPROVED, REJECTED).
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaveStatus status;

    /**
     * Optional details or reason for the leave.
     */
    @Column(name = "reason", nullable = false)
    @Length(min = 20, max = 1000)
    private String reason;
    /**
     * The employee who requested the leave.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
