package com.humano.domain.payroll;

import com.humano.converters.TimeZoneConverter;
import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.payroll.Frequency;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.TimeZone;
import java.util.UUID;

/**
 * PayrollCalendar defines the schedule and recurrence of payroll periods for an organization.
 * <p>
 * It acts as a template for payroll processing, specifying the name, frequency (e.g., monthly, biweekly),
 * timezone context, and whether the calendar is currently active. PayrollPeriod entities are generated based
 * on this calendar, and all payroll runs reference a PayrollCalendar to determine their timing and rules.
 * <ul>
 *   <li><b>name</b>: Human-readable name for the calendar (e.g., "Albania Monthly").</li>
 *   <li><b>frequency</b>: How often payroll is processed (e.g., MONTHLY, BIWEEKLY).</li>
 *   <li><b>timezone</b>: The timezone in which payroll dates are interpreted (e.g., "Europe/Tirane").</li>
 *   <li><b>active</b>: Indicates if the calendar is currently in use for payroll processing.</li>
 * </ul>
 * <p>
 * PayrollCalendar is essential for organizations with multiple payroll cycles, international operations,
 * or complex payroll scheduling needs. It ensures payroll periods are generated and processed consistently
 * according to business requirements.
 */
@Entity
@Table(name = "payroll_calendar")
public class PayrollCalendar extends AbstractAuditingEntity<UUID> {
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

    @Column(name = "name", nullable = false)
    private String name; // e.g., "Albania Monthly"

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency; // MONTHLY, BIWEEKLY...

    @Column(name = "timezone", nullable = false)
    @Convert(converter = TimeZoneConverter.class)
    private TimeZone timezone; // e.g., "Europe/Tirane"

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
