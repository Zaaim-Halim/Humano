package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks changes in an employee's position, salary, and organizational unit over time.
 * <p>
 * Used for auditing promotions, transfers, and other position changes.
 */
@Entity
@Table(name = "employee_position_history")
public class EmployeePositionHistory extends AbstractAuditingEntity<UUID> {
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
     * Previous salary before the change.
     */
    @Column(name = "old_salary")
    private BigDecimal oldSalary;
    /**
     * New salary after the change.
     */
    @Column(name = "new_salary")

    private BigDecimal newSalary;
    /**
     * Date the change takes effect.
     */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;
    /**
     * Type of position change (e.g., PROMOTION, TRANSFER).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private PositionChangeType changeType;
    /**
     * Status of the change (e.g., PENDING, APPLIED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PositionChangeStatus status; // PENDING, APPLIED, CANCELLED
    /**
     * Reason for the change.
     */
    @Column(name = "reason")
    private String reason;
    /**
     * The employee affected by the change.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;
    /**
     * Previous position before the change.
     */
    @ManyToOne
    @JoinColumn(name = "old_position_id")
    private Position oldPosition;
    /**
     * New position after the change.
     */
    @ManyToOne
    @JoinColumn(name = "new_position_id")
    private Position newPosition;
    /**
     * Previous organizational unit before the change.
     */
    @ManyToOne
    @JoinColumn(name = "old_unit_id")
    private OrganizationalUnit oldUnit;
    /**
     * New organizational unit after the change.
     */
    @ManyToOne
    @JoinColumn(name = "new_unit_id")
    private OrganizationalUnit newUnit;

    @ManyToOne
    @JoinColumn(name = "old_manager_id")
    private Employee oldManager;

    @ManyToOne
    @JoinColumn(name = "new_manager_id")
    private Employee newManager;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getOldSalary() {
        return oldSalary;
    }

    public EmployeePositionHistory oldSalary(BigDecimal oldSalary) {
        this.oldSalary = oldSalary;
        return this;
    }

    public void setOldSalary(BigDecimal oldSalary) {
        this.oldSalary = oldSalary;
    }

    public BigDecimal getNewSalary() {
        return newSalary;
    }

    public EmployeePositionHistory newSalary(BigDecimal newSalary) {
        this.newSalary = newSalary;
        return this;
    }

    public void setNewSalary(BigDecimal newSalary) {
        this.newSalary = newSalary;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public EmployeePositionHistory effectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
        return this;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public PositionChangeType getChangeType() {
        return changeType;
    }

    public EmployeePositionHistory changeType(PositionChangeType changeType) {
        this.changeType = changeType;
        return this;
    }

    public void setChangeType(PositionChangeType changeType) {
        this.changeType = changeType;
    }

    public PositionChangeStatus getStatus() {
        return status;
    }

    public EmployeePositionHistory status(PositionChangeStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(PositionChangeStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public EmployeePositionHistory reason(String reason) {
        this.reason = reason;
        return this;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeePositionHistory employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Position getOldPosition() {
        return oldPosition;
    }

    public EmployeePositionHistory oldPosition(Position oldPosition) {
        this.oldPosition = oldPosition;
        return this;
    }

    public void setOldPosition(Position oldPosition) {
        this.oldPosition = oldPosition;
    }

    public Position getNewPosition() {
        return newPosition;
    }

    public EmployeePositionHistory newPosition(Position newPosition) {
        this.newPosition = newPosition;
        return this;
    }

    public void setNewPosition(Position newPosition) {
        this.newPosition = newPosition;
    }

    public OrganizationalUnit getOldUnit() {
        return oldUnit;
    }

    public EmployeePositionHistory oldUnit(OrganizationalUnit oldUnit) {
        this.oldUnit = oldUnit;
        return this;
    }

    public void setOldUnit(OrganizationalUnit oldUnit) {
        this.oldUnit = oldUnit;
    }

    public OrganizationalUnit getNewUnit() {
        return newUnit;
    }

    public EmployeePositionHistory newUnit(OrganizationalUnit newUnit) {
        this.newUnit = newUnit;
        return this;
    }

    public void setNewUnit(OrganizationalUnit newUnit) {
        this.newUnit = newUnit;
    }

    public Employee getOldManager() {
        return oldManager;
    }

    public EmployeePositionHistory oldManager(Employee oldManager) {
        this.oldManager = oldManager;
        return this;
    }

    public void setOldManager(Employee oldManager) {
        this.oldManager = oldManager;
    }

    public Employee getNewManager() {
        return newManager;
    }

    public EmployeePositionHistory newManager(Employee newManager) {
        this.newManager = newManager;
        return this;
    }

    public void setNewManager(Employee newManager) {
        this.newManager = newManager;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeePositionHistory that = (EmployeePositionHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeePositionHistory{" +
            "id=" + id +
            ", effectiveDate=" + effectiveDate +
            ", changeType=" + changeType +
            ", status=" + status +
            '}';
    }
}
