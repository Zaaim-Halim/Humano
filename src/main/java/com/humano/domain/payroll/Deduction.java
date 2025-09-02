package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.DeductionType;
import com.humano.domain.hr.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Deduction entity represents amounts deducted from an employee's pay.
 * <p>
 * This includes tax withholdings, insurance premiums, retirement contributions,
 * garnishments, and other pre-tax or post-tax deductions from employee pay.
 * <p>
 * Deductions are a critical component of payroll processing that affect net pay calculations,
 * tax reporting, and benefit administration. They can be either fixed amounts or percentage-based
 * and may be applicable for specific time periods.
 */
@Entity
@Table(name = "deduction")
public class Deduction extends AbstractAuditingEntity<UUID> {

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
     * The employee from whose pay the deduction is taken.
     * <p>
     * Links to the Employee entity in the HR domain to maintain
     * relationship between deductions and employees.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The type of deduction (TAX, INSURANCE, RETIREMENT, GARNISHMENT, OTHER).
     * <p>
     * Categorizes the deduction for reporting and processing purposes.
     * Different types may have different tax implications and processing rules.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DeductionType type;

    /**
     * The fixed amount of the deduction in the specified currency.
     * <p>
     * Used when the deduction is a fixed monetary amount rather than
     * a percentage of pay. Either amount or percentage should be set, not both.
     * <p>
     * Minimum value is 0, as deductions cannot be negative.
     */
    @Column(name = "amount")
    @DecimalMin(value = "0.0", inclusive = true, message = "Deduction amount cannot be negative")
    @DecimalMax(value = "1000000.0", message = "Deduction amount exceeds maximum allowed value")
    private BigDecimal amount;

    /**
     * The percentage rate of the deduction if it's percentage-based.
     * <p>
     * Used when the deduction is calculated as a percentage of the employee's
     * eligible pay. Either percentage or amount should be set, not both.
     * <p>
     * Minimum value is 0% and maximum is 100%, as deductions cannot exceed total pay.
     */
    @Column(name = "percentage")
    @DecimalMin(value = "0.0", inclusive = true, message = "Deduction percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Deduction percentage cannot exceed 100%")
    private BigDecimal percentage;

    /**
     * The date from which this deduction becomes effective.
     * <p>
     * Used to track when a deduction should start being applied to payroll calculations.
     */
    @Column(name = "effective_from", nullable = false)
    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    /**
     * The date until which this deduction is effective.
     * <p>
     * Optional field used to track when a deduction should no longer be applied.
     * If null, the deduction is considered ongoing until explicitly terminated.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Human-readable description of the deduction.
     * <p>
     * Provides additional context about the purpose or details of the deduction.
     */
    @Column(name = "description", nullable = false)
    @NotNull(message = "Description is required")
    @Size(min = 3, max = 255, message = "Description must be between 3 and 255 characters")
    private String description;

    /**
     * Indicates whether the deduction is taken before taxes are calculated.
     * <p>
     * Pre-tax deductions reduce taxable income before tax calculations,
     * while post-tax deductions do not affect taxable income.
     */
    @Column(name = "is_pre_tax", nullable = false)
    @NotNull(message = "Pre-tax status is required")
    private Boolean isPreTax = false;

    /**
     * The currency of the deduction amount.
     * <p>
     * Links to the Currency entity to specify the currency in which
     * the deduction amount is denominated.
     */
    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Deduction employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public DeductionType getType() {
        return type;
    }

    public Deduction type(DeductionType type) {
        this.type = type;
        return this;
    }

    public void setType(DeductionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Deduction amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public Deduction percentage(BigDecimal percentage) {
        this.percentage = percentage;
        return this;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Deduction effectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
        return this;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public Deduction effectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
        return this;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getDescription() {
        return description;
    }

    public Deduction description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsPreTax() {
        return isPreTax;
    }

    public Deduction isPreTax(Boolean isPreTax) {
        this.isPreTax = isPreTax;
        return this;
    }

    public void setIsPreTax(Boolean isPreTax) {
        this.isPreTax = isPreTax;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Deduction currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deduction deduction = (Deduction) o;
        return Objects.equals(id, deduction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Deduction{" +
            "id=" + id +
            ", type=" + type +
            ", amount=" + amount +
            ", percentage=" + percentage +
            ", effectiveFrom=" + effectiveFrom +
            ", effectiveTo=" + effectiveTo +
            ", description='" + description + '\'' +
            ", isPreTax=" + isPreTax +
            '}';
    }
}
