package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.hr.Employee;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PayrollInput entity representing individual payroll inputs for employees.
 * Each input can be linked to a specific payroll period and component.
 * It supports quantity, rate, and amount fields to accommodate various input types.
 * Metadata can be stored in JSON format for additional context.
 * The source field indicates how the input was generated (e.g., manual entry, system integration).
 * <p>
 * Example components:
 * - Overtime hours
 * - Bonuses
 * - Deductions
 * <p>
 * This entity is designed to be flexible to handle diverse payroll scenarios.
 *
 * @author halimzaaim
 */
@Entity
@Table(name = "payroll_input")
public class PayrollInput extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "quantity")
    private BigDecimal quantity; // hours/units

    @Column(name = "rate")
    private BigDecimal rate; // optional per-input rate

    @Column(name = "amount")
    private BigDecimal amount; // optional direct amount

    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson; // notes/attachment refs

    @Column(name = "source")
    private String source; // "Attendance", "Manual", "Integration"

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private PayrollPeriod period;

    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    private PayComponent component; // e.g., "OT_HOURS", "BONUS"

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}

