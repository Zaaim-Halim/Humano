package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * PayComponent → PayRule → PayrollLine → PayrollResult (net salary)
 * PayrollLine represents a single calculated payroll item for an employee in a specific payroll period.
 * <p>
 * It is the result of applying a PayComponent (such as BASIC, OT, TAX) and its associated PayRule to an employee
 * for a given PayrollResult. Each PayrollLine stores the calculated amount, quantity, rate, and references the
 * PayComponent and PayrollResult it belongs to. PayrollLine is the atomic unit of payroll calculation, allowing
 * detailed breakdowns of earnings, deductions, and employer charges on payslips and payroll reports.
 * <ul>
 *   <li><b>component</b>: The PayComponent (e.g., BASIC, OT, TAX) this line represents.</li>
 *   <li><b>result</b>: The PayrollResult (payslip) this line is part of.</li>
 *   <li><b>quantity</b>: The number of units/hours for this component (if applicable).</li>
 *   <li><b>rate</b>: The rate applied for this component (if applicable).</li>
 *   <li><b>amount</b>: The calculated monetary value for this line.</li>
 * </ul>
 * <p>
 * PayrollLine enables granular payroll analysis, auditing, and reporting, and is essential for generating
 * detailed payslips and supporting compliance requirements.
 */
@Entity
@Table(name = "payroll_line")
public class PayrollLine extends AbstractAuditingEntity<UUID> {
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
     * The number of units/hours for this component (if applicable).
     * <p>
     * Used for quantity-based components like hours worked or units produced.
     * Can be null for fixed components that don't depend on quantity.
     */
    @Column(name = "quantity")
    private BigDecimal quantity;

    /**
     * The rate applied for this component (if applicable).
     * <p>
     * Used with the quantity to calculate the amount for rate-based components.
     * Can be null for fixed components that don't depend on a rate.
     */
    @Column(name = "rate")
    private BigDecimal rate;

    /**
     * The calculated monetary value for this line.
     * <p>
     * Represents the final value of this payroll component after all calculations.
     * For earnings, this is a positive value; for deductions, this is a negative value.
     */
    @Column(name = "amount", nullable = false)
    @NotNull(message = "Amount is required")
    private BigDecimal amount = BigDecimal.ZERO;

    /**
     * The display sequence for this line on payslips and reports.
     * <p>
     * Determines the order in which payroll lines appear in outputs.
     * Lower numbers appear before higher numbers.
     */
    @Column(name = "sequence")
    private Integer sequence;

    /**
     * Detailed explanation of how this line was calculated.
     * <p>
     * Provides transparency and auditability for payroll calculations.
     */
    @Column(name = "explain", columnDefinition = "TEXT")
    private String explain;

    /**
     * The PayrollResult (payslip) this line is part of.
     * <p>
     * Links to the parent result that aggregates all payroll lines for an employee.
     */
    @ManyToOne
    @JoinColumn(name = "result_id", nullable = false)
    @NotNull(message = "Payroll result is required")
    private PayrollResult result;

    /**
     * The PayComponent (e.g., BASIC, OT, TAX) this line represents.
     * <p>
     * Identifies the type of pay component for categorization and reporting.
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

    public PayrollLine quantity(BigDecimal quantity) {
        this.quantity = quantity;
        return this;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public PayrollLine rate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PayrollLine amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getSequence() {
        return sequence;
    }

    public PayrollLine sequence(Integer sequence) {
        this.sequence = sequence;
        return this;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public String getExplain() {
        return explain;
    }

    public PayrollLine explain(String explain) {
        this.explain = explain;
        return this;
    }

    public void setExplain(String explain) {
        this.explain = explain;
    }

    public PayrollResult getResult() {
        return result;
    }

    public PayrollLine result(PayrollResult result) {
        this.result = result;
        return this;
    }

    public void setResult(PayrollResult result) {
        this.result = result;
    }

    public PayComponent getComponent() {
        return component;
    }

    public PayrollLine component(PayComponent component) {
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
        PayrollLine that = (PayrollLine) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PayrollLine{" +
            "id=" + id +
            ", component=" + (component != null ? component.getCode() : null) +
            ", amount=" + amount +
            ", quantity=" + quantity +
            ", rate=" + rate +
            ", sequence=" + sequence +
            '}';
    }
}
