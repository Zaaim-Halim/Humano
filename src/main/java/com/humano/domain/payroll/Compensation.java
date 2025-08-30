package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.Position;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false)
    private Basis basis;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo; // null = open-ended

    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "position_id", nullable = false)
    private Position position; //this should be redundant, but for history purposes

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
