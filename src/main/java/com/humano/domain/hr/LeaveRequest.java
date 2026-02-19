package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.validator.constraints.Length;

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
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Start date of the leave.
     */
    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * End date of the leave.
     */
    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Type of leave (e.g., SICK, VACATION, UNPAID).
     */
    @NotNull
    @Column(name = "leave_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    /**
     * Status of the leave request (e.g., PENDING, APPROVED, REJECTED).
     */
    @NotNull
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
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The employee who approved/rejected the leave request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Employee approver;

    /**
     * Comments provided by the approver when approving/rejecting the request.
     */
    @Column(name = "approver_comments")
    private String approverComments;

    /**
     * Number of business days the leave spans.
     */
    @Column(name = "days_count")
    private Integer daysCount;

    @PrePersist
    @PreUpdate
    public void validateDates() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        // Calculate days count (simplified - you might want to exclude weekends and holidays)
        if (startDate != null && endDate != null) {
            this.daysCount = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LeaveRequest startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LeaveRequest endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LeaveRequest leaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
        return this;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public LeaveRequest status(LeaveStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public LeaveRequest reason(String reason) {
        this.reason = reason;
        return this;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveRequest employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Employee getApprover() {
        return approver;
    }

    public LeaveRequest approver(Employee approver) {
        this.approver = approver;
        return this;
    }

    public void setApprover(Employee approver) {
        this.approver = approver;
    }

    public String getApproverComments() {
        return approverComments;
    }

    public LeaveRequest approverComments(String approverComments) {
        this.approverComments = approverComments;
        return this;
    }

    public void setApproverComments(String approverComments) {
        this.approverComments = approverComments;
    }

    public Integer getDaysCount() {
        return daysCount;
    }

    public LeaveRequest daysCount(Integer daysCount) {
        this.daysCount = daysCount;
        return this;
    }

    public void setDaysCount(Integer daysCount) {
        this.daysCount = daysCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveRequest that = (LeaveRequest) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "LeaveRequest{" +
            "id=" +
            id +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            ", leaveType=" +
            leaveType +
            ", status=" +
            status +
            ", daysCount=" +
            daysCount +
            '}'
        );
    }
}
