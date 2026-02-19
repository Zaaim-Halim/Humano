package com.humano.domain.payroll;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * PayRule defines the calculation logic for a PayComponent in the payroll system.
 * <p>
 * It specifies who the rule applies to (scope and target), when it is valid (validFrom/validTo),
 * what component it affects, and how the calculation is performed (formula). PayRule supports
 * flexible, data-driven payroll logic using expression engines (e.g., SpEL, MVEL), allowing rules
 * to be added or modified without code changes. Precedence and priority fields help resolve
 * conflicts when multiple rules apply.
 * <ul>
 *   <li><b>scope</b>: Defines the applicability of the rule (GLOBAL, COUNTRY, UNIT, POSITION, EMPLOYEE).</li>
 *   <li><b>target</b>: Identifier for the scope (e.g., country code, unit ID, position ID, employee ID).</li>
 *   <li><b>component</b>: The PayComponent this rule applies to.</li>
 *   <li><b>formula</b>: The calculation logic, written in a supported expression language (SPEL).</li>
 *   <li><b>validFrom/validTo</b>: The period during which the rule is active.</li>
 *   <li><b>precedence</b>: Used to resolve conflicts when multiple rules apply; higher wins.</li>
 *   <li><b>active</b>: Indicates if the rule is currently in use.</li>
 * </ul>
 * <p>
 * PayRule enables highly configurable payroll calculations, supporting country-specific, unit-specific,
 * position-specific, and employee-specific logic, and is essential for compliance and business flexibility.
 */
@Entity
@Table(name = "pay_rule")
public class PayRule extends AbstractAuditingEntity<UUID> {

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
     * The calculation logic, written in a supported expression language (SPEL).
     * <p>
     * Contains the formula that determines how this pay component is calculated.
     * For example: "basicSalary + overtime"
     */
    @Column(name = "formula", nullable = false, columnDefinition = "TEXT")
    @NotNull(message = "Formula is required")
    private String formula;

    /**
     * The date from which this rule becomes effective.
     * <p>
     * Used to track when a calculation rule should start being applied.
     */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /**
     * The date until which this rule is effective.
     * <p>
     * Optional field used to track when a rule should no longer be applied.
     * If null, the rule is considered ongoing until explicitly terminated.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Used to resolve conflicts when multiple rules apply.
     * <p>
     * Higher priority rules take precedence over lower priority rules
     * when multiple rules could apply to the same situation.
     */
    @Column(name = "priority")
    private Integer priority;

    /**
     * References to other component codes used in the formula.
     * <p>
     * Used to track dependencies between pay components for calculation ordering.
     */
    @Column(name = "base_formula_ref")
    @Size(max = 255, message = "Base formula reference cannot exceed 255 characters")
    private String baseFormulaRef;

    /**
     * Indicates if the rule is currently in use.
     * <p>
     * Used to enable or disable rules without deleting them.
     * Default is true (active).
     */
    @Column(name = "active", nullable = false)
    @NotNull(message = "Active status is required")
    private Boolean active = true;

    /**
     * The PayComponent this rule applies to.
     * <p>
     * Links to the component that this rule calculates.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_component_id", nullable = false)
    @NotNull(message = "Pay component is required")
    private PayComponent payComponent;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFormula() {
        return formula;
    }

    public PayRule formula(String formula) {
        this.formula = formula;
        return this;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public PayRule effectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
        return this;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public PayRule effectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
        return this;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Integer getPriority() {
        return priority;
    }

    public PayRule priority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getBaseFormulaRef() {
        return baseFormulaRef;
    }

    public PayRule baseFormulaRef(String baseFormulaRef) {
        this.baseFormulaRef = baseFormulaRef;
        return this;
    }

    public void setBaseFormulaRef(String baseFormulaRef) {
        this.baseFormulaRef = baseFormulaRef;
    }

    public Boolean getActive() {
        return active;
    }

    public PayRule active(Boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public PayComponent getPayComponent() {
        return payComponent;
    }

    public PayRule payComponent(PayComponent payComponent) {
        this.payComponent = payComponent;
        return this;
    }

    public void setPayComponent(PayComponent payComponent) {
        this.payComponent = payComponent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayRule payRule = (PayRule) o;
        return Objects.equals(id, payRule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "PayRule{" +
            "id=" +
            id +
            ", formula='" +
            (formula != null ? formula.substring(0, Math.min(formula.length(), 30)) + "..." : null) +
            '\'' +
            ", effectiveFrom=" +
            effectiveFrom +
            ", effectiveTo=" +
            effectiveTo +
            ", priority=" +
            priority +
            ", active=" +
            active +
            '}'
        );
    }
}
