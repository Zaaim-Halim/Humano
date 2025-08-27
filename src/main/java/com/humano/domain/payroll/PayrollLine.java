package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
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
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "sequence")
    private Integer sequence;

    @Column(name = "explain", columnDefinition = "TEXT")
    private String explain;

    @ManyToOne
    @JoinColumn(name = "result_id", nullable = false)
    private PayrollResult result;

    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    private PayComponent component;

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
