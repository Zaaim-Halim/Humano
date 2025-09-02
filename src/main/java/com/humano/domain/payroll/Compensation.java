package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.Position;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Compensation entity represents the salary or wage arrangement for an employee.
 * <p>
 * It links an employee to a position and defines the base amount, payment basis (monthly, annual, hourly),
 * currency, and the period during which this compensation is effective. This entity supports historical tracking
 * of salary changes, multi-currency payroll, and is the foundation for payroll calculations.
 * <ul>
 *   <li><b>baseAmount</b>: The salary or wage amount for the period or annually, depending on basis.</li>
 *   <li><b>basis</b>: Indicates if the compensation is monthly, annual, or hourly.</li>
 *   <li><b>effectiveFrom/effectiveTo</b>: The period during which this compensation is valid. Null effectiveTo means open-ended.</li>
 *   <li><b>currency</b>: The currency in which the compensation is paid.</li>
 *   <li><b>employee</b>: The employee receiving this compensation.</li>
 *   <li><b>position</b>: The position for which the compensation applies, supporting historical and role-based payroll.</li>
 * </ul>
 * <p>
 * During payroll calculation, the system finds the active Compensation for the employee and period, and uses its
 * baseAmount, basis, and currency as the starting point for gross pay. All payroll rules, inputs, and adjustments
 * are applied on top of this entity.
 */

@Entity
@Table(name = "compensation")
public class Compensation extends AbstractAuditingEntity<UUID> {
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
     * The salary or wage amount for the period or annually, depending on basis.
     * <p>
     * This is the base compensation before any adjustments, bonuses, or deductions.
     * Default value is 0.
     */
    @Column(name = "base_amount", nullable = false)
    @NotNull(message = "Base amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Base amount cannot be negative")
    private BigDecimal baseAmount = BigDecimal.ZERO;

    /**
     * Indicates if the compensation is monthly, annual, or hourly.
     * <p>
     * Determines how the baseAmount is interpreted and calculated for payroll.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false)
    @NotNull(message = "Basis is required")
    private Basis basis;

    /**
     * The date from which this compensation becomes effective.
     * <p>
     * Used to track when a compensation rate should start being applied.
     */
    @Column(name = "effective_from", nullable = false)
    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    /**
     * The date until which this compensation is effective.
     * <p>
     * Optional field used to track when a compensation rate should no longer be applied.
     * If null, the compensation is considered ongoing until explicitly terminated.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * The currency in which the compensation is paid.
     * <p>
     * Links to the Currency entity to specify the currency of the base amount.
     */
    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    @NotNull(message = "Currency is required")
    private Currency currency;

    /**
     * The employee receiving this compensation.
     * <p>
     * Links to the Employee entity to associate compensation with specific employees.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @NotNull(message = "Employee is required")
    private Employee employee;

    /**
     * The position for which the compensation applies.
     * <p>
     * Supports historical tracking and role-based payroll by linking compensation to positions.
     */
    @ManyToOne
    @JoinColumn(name = "position_id", nullable = false)
    @NotNull(message = "Position is required")
    private Position position;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public Compensation baseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
        return this;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public Basis getBasis() {
        return basis;
    }

    public Compensation basis(Basis basis) {
        this.basis = basis;
        return this;
    }

    public void setBasis(Basis basis) {
        this.basis = basis;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Compensation effectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
        return this;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public Compensation effectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
        return this;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Compensation currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Compensation employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Position getPosition() {
        return position;
    }

    public Compensation position(Position position) {
        this.position = position;
        return this;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Compensation that = (Compensation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Compensation{" +
            "id=" + id +
            ", baseAmount=" + baseAmount +
            ", basis=" + basis +
            ", effectiveFrom=" + effectiveFrom +
            ", effectiveTo=" + effectiveTo +
            ", currency=" + (currency != null ? currency.getCode() : null) +
            '}';
    }
}
