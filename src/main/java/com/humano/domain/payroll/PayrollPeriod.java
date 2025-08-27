package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * PayrollPeriod represents a single payroll cycle within a PayrollCalendar.
 * <p>
 * It defines the start and end dates for the payroll period, the scheduled payment date,
 * and whether the period is closed (locked for posting and further changes). Each PayrollPeriod
 * is linked to a PayrollCalendar, which determines its recurrence and scheduling rules.
 * <ul>
 *   <li><b>calendar</b>: The PayrollCalendar that defines the recurrence and rules for this period.</li>
 *   <li><b>startDate</b>: The first day of the payroll period.</li>
 *   <li><b>endDate</b>: The last day of the payroll period.</li>
 *   <li><b>paymentDate</b>: The date on which payroll is scheduled to be paid.</li>
 *   <li><b>closed</b>: Indicates if the period is locked for posting and cannot be modified.</li>
 * </ul>
 * <p>
 * PayrollPeriod is essential for organizing payroll runs, ensuring accurate calculation windows,
 * and supporting compliance with business and legal requirements for payroll timing.
 */
@Entity
@Table(name = "payroll_period")
public class PayrollPeriod extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "closed", nullable = false)
    private boolean closed; // locked for posting

    @Column(name = "code", unique = true, nullable = false)
    private String code; // "2025-08-MONTHLY-ALB"

    @ManyToOne
    @JoinColumn(name = "calendar_id", nullable = false)
    private PayrollCalendar calendar;

    @Override
    public UUID getId() {
        return null;
    }

    // Getters and setters can be added as needed
}
