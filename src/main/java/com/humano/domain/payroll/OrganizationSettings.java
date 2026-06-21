package com.humano.domain.payroll;

import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Company-level payroll policy / settings.
 * <p>
 * A <b>singleton per tenant schema</b> (one company per tenant), holding the
 * defaults the payroll engine and HR screens fall back to: standard working
 * hours (day/week/month), the default pay {@link Basis}, the default overtime
 * multiplier, an optional default payroll calendar and reporting currency, and
 * the company timezone. The hardcoded "160 monthly hours" in
 * {@code PayrollProcessingService.calculateBaseSalary} is the concrete value
 * this object is meant to make configurable.
 */
@Entity
@Table(name = "organization_settings")
public class OrganizationSettings extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Standard hours in a working day (default 8). */
    @Column(name = "standard_hours_per_day", nullable = false)
    private BigDecimal standardHoursPerDay = BigDecimal.valueOf(8);

    /** Standard hours in a working week (default 40). */
    @Column(name = "standard_hours_per_week", nullable = false)
    private BigDecimal standardHoursPerWeek = BigDecimal.valueOf(40);

    /** Standard hours in a month — the HOURLY→monthly conversion factor (default 160). */
    @Column(name = "standard_monthly_hours", nullable = false)
    private BigDecimal standardMonthlyHours = BigDecimal.valueOf(160);

    /** Default pay basis applied when none is specified (default MONTHLY). */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_basis", nullable = false)
    private Basis defaultBasis = Basis.MONTHLY;

    /** Default overtime multiplier (default 1.5×). */
    @Column(name = "default_overtime_multiplier", nullable = false)
    private BigDecimal defaultOvertimeMultiplier = BigDecimal.valueOf(1.5);

    /** Company timezone (IANA id, default UTC). */
    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";

    /** Optional default reporting currency. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_currency_id")
    private Currency defaultCurrency;

    /** Optional default payroll calendar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_payroll_calendar_id")
    private PayrollCalendar defaultPayrollCalendar;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getStandardHoursPerDay() {
        return standardHoursPerDay;
    }

    public void setStandardHoursPerDay(BigDecimal standardHoursPerDay) {
        this.standardHoursPerDay = standardHoursPerDay;
    }

    public BigDecimal getStandardHoursPerWeek() {
        return standardHoursPerWeek;
    }

    public void setStandardHoursPerWeek(BigDecimal standardHoursPerWeek) {
        this.standardHoursPerWeek = standardHoursPerWeek;
    }

    public BigDecimal getStandardMonthlyHours() {
        return standardMonthlyHours;
    }

    public void setStandardMonthlyHours(BigDecimal standardMonthlyHours) {
        this.standardMonthlyHours = standardMonthlyHours;
    }

    public Basis getDefaultBasis() {
        return defaultBasis;
    }

    public void setDefaultBasis(Basis defaultBasis) {
        this.defaultBasis = defaultBasis;
    }

    public BigDecimal getDefaultOvertimeMultiplier() {
        return defaultOvertimeMultiplier;
    }

    public void setDefaultOvertimeMultiplier(BigDecimal defaultOvertimeMultiplier) {
        this.defaultOvertimeMultiplier = defaultOvertimeMultiplier;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Currency getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(Currency defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public PayrollCalendar getDefaultPayrollCalendar() {
        return defaultPayrollCalendar;
    }

    public void setDefaultPayrollCalendar(PayrollCalendar defaultPayrollCalendar) {
        this.defaultPayrollCalendar = defaultPayrollCalendar;
    }
}
