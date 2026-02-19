package com.humano.domain.payroll;

import com.humano.converters.TimeZoneConverter;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Human-readable name for the calendar (e.g., "Albania Monthly").
     * <p>
     * Provides a descriptive identifier for the payroll calendar.
     */
    @Column(name = "name", nullable = false)
    @NotNull(message = "Calendar name is required")
    @Size(min = 2, max = 255, message = "Calendar name must be between 2 and 255 characters")
    private String name;

    /**
     * How often payroll is processed (e.g., MONTHLY, BIWEEKLY).
     * <p>
     * Determines the frequency of payroll periods generated from this calendar.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    @NotNull(message = "Frequency is required")
    private Frequency frequency;

    /**
     * The timezone in which payroll dates are interpreted (e.g., "Europe/Tirane").
     * <p>
     * Ensures consistent date handling across different geographic locations.
     */
    @Column(name = "timezone", nullable = false)
    @Convert(converter = TimeZoneConverter.class)
    @NotNull(message = "Timezone is required")
    private TimeZone timezone;

    /**
     * Indicates if the calendar is currently in use for payroll processing.
     * <p>
     * Used to enable or disable payroll calendars without deleting them.
     * Default is true (active).
     */
    @Column(name = "active", nullable = false)
    @NotNull(message = "Active status is required")
    private Boolean active = true;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public PayrollCalendar name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public PayrollCalendar frequency(Frequency frequency) {
        this.frequency = frequency;
        return this;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public TimeZone getTimezone() {
        return timezone;
    }

    public PayrollCalendar timezone(TimeZone timezone) {
        this.timezone = timezone;
        return this;
    }

    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    public Boolean getActive() {
        return active;
    }

    public PayrollCalendar active(Boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayrollCalendar that = (PayrollCalendar) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "PayrollCalendar{" +
            "id=" +
            id +
            ", name='" +
            name +
            '\'' +
            ", frequency=" +
            frequency +
            ", timezone=" +
            timezone +
            ", active=" +
            active +
            '}'
        );
    }
}
