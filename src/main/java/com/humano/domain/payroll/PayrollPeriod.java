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
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The first day of the payroll period.
     * <p>
     * Marks the beginning of the time period for which payroll is calculated.
     */
    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    /**
     * The last day of the payroll period.
     * <p>
     * Marks the end of the time period for which payroll is calculated.
     */
    @Column(name = "end_date", nullable = false)
    @NotNull(message = "End date is required")
    private LocalDate endDate;

    /**
     * The date on which payroll is scheduled to be paid.
     * <p>
     * Determines when employees will receive their compensation.
     */
    @Column(name = "payment_date", nullable = false)
    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    /**
     * Indicates if the period is locked for posting and cannot be modified.
     * <p>
     * Used to prevent changes to finalized payroll periods.
     * Default is false (period is open for changes).
     */
    @Column(name = "closed", nullable = false)
    private boolean closed = false;

    /**
     * Unique code for the payroll period (e.g., "2025-08-MONTHLY-ALB").
     * <p>
     * Provides a human-readable identifier for the period.
     */
    @Column(name = "code", unique = true, nullable = false)
    @NotNull(message = "Period code is required")
    @Size(min = 3, max = 100, message = "Period code must be between 3 and 100 characters")
    private String code;

    /**
     * The PayrollCalendar that defines the recurrence and rules for this period.
     * <p>
     * Links the period to its parent calendar for organizational purposes.
     */
    @ManyToOne
    @JoinColumn(name = "calendar_id", nullable = false)
    private PayrollCalendar calendar;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public PayrollPeriod startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PayrollPeriod endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public PayrollPeriod paymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
        return this;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public boolean isClosed() {
        return closed;
    }

    public PayrollPeriod closed(boolean closed) {
        this.closed = closed;
        return this;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public String getCode() {
        return code;
    }

    public PayrollPeriod code(String code) {
        this.code = code;
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public PayrollCalendar getCalendar() {
        return calendar;
    }

    public PayrollPeriod calendar(PayrollCalendar calendar) {
        this.calendar = calendar;
        return this;
    }

    public void setCalendar(PayrollCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayrollPeriod that = (PayrollPeriod) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "PayrollPeriod{" +
            "id=" +
            id +
            ", code='" +
            code +
            '\'' +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            ", paymentDate=" +
            paymentDate +
            ", closed=" +
            closed +
            '}'
        );
    }
}
