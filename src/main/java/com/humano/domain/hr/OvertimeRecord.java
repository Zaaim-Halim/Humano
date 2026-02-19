package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.enumeration.hr.OvertimeType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
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
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public OvertimeRecord date(LocalDate date) {
        this.date = date;
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public OvertimeRecord hours(BigDecimal hours) {
        this.hours = hours;
        return this;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public OvertimeType getType() {
        return type;
    }

    public OvertimeRecord type(OvertimeType type) {
        this.type = type;
        return this;
    }

    public void setType(OvertimeType type) {
        this.type = type;
    }

    public OvertimeApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public OvertimeRecord approvalStatus(OvertimeApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
        return this;
    }

    public void setApprovalStatus(OvertimeApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getNotes() {
        return notes;
    }

    public OvertimeRecord notes(String notes) {
        this.notes = notes;
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Employee getApprovedBy() {
        return approvedBy;
    }

    public OvertimeRecord approvedBy(Employee approvedBy) {
        this.approvedBy = approvedBy;
        return this;
    }

    public void setApprovedBy(Employee approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Employee getEmployee() {
        return employee;
    }

    public OvertimeRecord employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    /**
     * Approve this overtime record.
     *
     * @param approver The employee approving the overtime
     * @param notes Optional approval notes
     * @return This overtime record
     */
    public OvertimeRecord approve(Employee approver, String notes) {
        this.approvalStatus = OvertimeApprovalStatus.APPROVED;
        this.approvedBy = approver;
        if (notes != null && !notes.isBlank()) {
            this.notes = notes;
        }
        return this;
    }

    /**
     * Reject this overtime record.
     *
     * @param approver The employee rejecting the overtime
     * @param notes Rejection reason
     * @return This overtime record
     */
    public OvertimeRecord reject(Employee approver, String notes) {
        this.approvalStatus = OvertimeApprovalStatus.REJECTED;
        this.approvedBy = approver;
        this.notes = notes;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OvertimeRecord that = (OvertimeRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "OvertimeRecord{" +
            "id=" +
            id +
            ", date=" +
            date +
            ", hours=" +
            hours +
            ", type=" +
            type +
            ", approvalStatus=" +
            approvalStatus +
            '}'
        );
    }
}
