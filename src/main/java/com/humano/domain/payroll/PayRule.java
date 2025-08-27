package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.util.UUID;

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
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "formula", nullable = false, columnDefinition = "TEXT")
    private String formula; // e.g. "basicSalary + overtime"

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "base_formula_ref")
    private String baseFormulaRef; // Will refer to other component codes

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_component_id", nullable = false)
    private PayComponent payComponent;

    @Override
    public UUID getId() {
        return id;
    }
}
