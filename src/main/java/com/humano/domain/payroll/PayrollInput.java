package com.humano.domain.payroll;

import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * PayrollInput entity represents individual payroll inputs for employees.
 * <p>
 * It captures the various inputs needed for payroll calculations, such as overtime hours,
 * bonuses, and deductions. Each input is linked to a specific employee, payroll period,
 * and pay component. The entity provides flexibility through quantity, rate, and amount fields
 * to accommodate different types of inputs, and can store additional context in JSON format.
 * <ul>
 *   <li><b>employee</b>: The employee this input applies to.</li>
 *   <li><b>period</b>: The payroll period this input belongs to.</li>
 *   <li><b>component</b>: The pay component this input relates to (e.g., OT_HOURS, BONUS).</li>
 *   <li><b>quantity</b>: The number of units/hours (for unit-based inputs).</li>
 *   <li><b>rate</b>: The rate per unit (for rate-based inputs).</li>
 *   <li><b>amount</b>: The direct amount (for fixed-amount inputs).</li>
 *   <li><b>metaJson</b>: Additional metadata in JSON format (notes, attachments).</li>
 *   <li><b>source</b>: How the input was generated (e.g., Attendance, Manual, Integration).</li>
 * </ul>
 * <p>
 * This entity is designed to be flexible to handle diverse payroll scenarios and is a critical
 * component for accurate payroll processing.
 */
@Entity
@Table(name = "payroll_input")
public class PayrollInput extends AbstractAuditingEntity<UUID> {

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
     * The number of units/hours for this input.
     * <p>
     * Used for quantity-based inputs like overtime hours or units produced.
     * Can be null for fixed-amount inputs that don't depend on quantity.
     */
    @Column(name = "quantity")
    @DecimalMin(value = "0.0", inclusive = true, message = "Quantity cannot be negative")
    private BigDecimal quantity;

    /**
     * The rate per unit for this input.
     * <p>
     * Used with the quantity to calculate the amount for rate-based inputs.
     * Can be null for fixed inputs that don't depend on a rate.
     */
    @Column(name = "rate")
    @DecimalMin(value = "0.0", inclusive = true, message = "Rate cannot be negative")
    private BigDecimal rate;

    /**
     * The direct amount for this input.
     * <p>
     * Used for fixed-amount inputs that don't involve quantity and rate calculations.
     * Can be null for inputs calculated from quantity and rate.
     */
    @Column(name = "amount")
    @DecimalMin(value = "0.0", inclusive = true, message = "Amount cannot be negative")
    private BigDecimal amount;

    /**
     * Additional metadata in JSON format.
     * <p>
     * Can store notes, attachment references, or other contextual information
     * that doesn't fit into the standard fields.
     */
    @Column(name = "meta_json", columnDefinition = "TEXT")
    @Size(max = 4000, message = "Metadata JSON cannot exceed 4000 characters")
    private String metaJson;

    /**
     * How the input was generated.
     * <p>
     * Tracks the origin of the input data for auditing and validation purposes.
     * Examples: "Attendance", "Manual", "Integration"
     */
    @Column(name = "source")
    @Size(max = 50, message = "Source cannot exceed 50 characters")
    private String source;

    /**
     * The employee this input applies to.
     * <p>
     * Links to the employee record in the HR domain.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @NotNull(message = "Employee is required")
    private Employee employee;

    /**
     * The payroll period this input belongs to.
     * <p>
     * Links to the specific time period this input applies to.
     */
    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    @NotNull(message = "Payroll period is required")
    private PayrollPeriod period;

    /**
     * The pay component this input relates to.
     * <p>
     * Identifies the type of payroll component (e.g., OT_HOURS, BONUS)
     * for categorization and processing.
     */
    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    @NotNull(message = "Pay component is required")
    private PayComponent component;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public PayrollInput quantity(BigDecimal quantity) {
        this.quantity = quantity;
        return this;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public PayrollInput rate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PayrollInput amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMetaJson() {
        return metaJson;
    }

    public PayrollInput metaJson(String metaJson) {
        this.metaJson = metaJson;
        return this;
    }

    public void setMetaJson(String metaJson) {
        this.metaJson = metaJson;
    }

    public String getSource() {
        return source;
    }

    public PayrollInput source(String source) {
        this.source = source;
        return this;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Employee getEmployee() {
        return employee;
    }

    public PayrollInput employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public PayrollPeriod getPeriod() {
        return period;
    }

    public PayrollInput period(PayrollPeriod period) {
        this.period = period;
        return this;
    }

    public void setPeriod(PayrollPeriod period) {
        this.period = period;
    }

    public PayComponent getComponent() {
        return component;
    }

    public PayrollInput component(PayComponent component) {
        this.component = component;
        return this;
    }

    public void setComponent(PayComponent component) {
        this.component = component;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayrollInput that = (PayrollInput) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "PayrollInput{" +
            "id=" +
            id +
            ", employee=" +
            (employee != null ? employee.getId() : null) +
            ", component=" +
            (component != null ? component.getCode() : null) +
            ", quantity=" +
            quantity +
            ", rate=" +
            rate +
            ", amount=" +
            amount +
            ", source='" +
            source +
            '\'' +
            '}'
        );
    }
}
