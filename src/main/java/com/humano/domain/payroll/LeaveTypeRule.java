package com.humano.domain.payroll;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Country;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * LeaveTypeRule defines how different types of leave affect payroll calculations.
 * <p>
 * It specifies the deduction percentage for each leave type in a specific country,
 * and whether the leave affects the taxable salary. This entity is essential for
 * accurately calculating payroll when employees take different types of leave.
 * <ul>
 *   <li><b>country</b>: The country these leave rules apply to.</li>
 *   <li><b>leaveType</b>: The type of leave (e.g., ANNUAL, SICK, MATERNITY).</li>
 *   <li><b>deductionPercentage</b>: Percentage of salary to deduct for this leave type.</li>
 *   <li><b>affectsTaxableSalary</b>: Whether this leave type affects gross taxable salary.</li>
 * </ul>
 * <p>
 * These rules ensure consistent application of leave policies in payroll calculations
 * across the organization while respecting country-specific regulations.
 */
@Entity
@Table(name = "leave_type_rule", uniqueConstraints = @UniqueConstraint(columnNames = { "leave_type", "country_id" }))
public class LeaveTypeRule extends AbstractAuditingEntity<UUID> {

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
     * The country these leave rules apply to.
     * <p>
     * Leave rules are country-specific due to different labor laws and regulations.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", nullable = false)
    @NotNull(message = "Country is required")
    private Country country;

    /**
     * The type of leave (e.g., ANNUAL, SICK, MATERNITY).
     * <p>
     * Different leave types may have different effects on payroll calculations.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    /**
     * Percentage of salary to deduct for this leave type.
     * <p>
     * Determines how much of an employee's salary is deducted when taking this type of leave.
     * Value range is 0.0 (no deduction) to 100.0 (full deduction).
     */
    @Column(nullable = false, precision = 5, scale = 2)
    @NotNull(message = "Deduction percentage is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Deduction percentage cannot be negative")
    @DecimalMax(value = "100.0", inclusive = true, message = "Deduction percentage cannot exceed 100%")
    private BigDecimal deductionPercentage;

    /**
     * Whether this leave type affects gross taxable salary.
     * <p>
     * Determines if the leave deduction affects the taxable income calculation.
     * Default is false (leave does not affect taxable salary).
     */
    @Column(nullable = false)
    @NotNull(message = "Affects taxable salary flag is required")
    private Boolean affectsTaxableSalary = false;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Country getCountry() {
        return country;
    }

    public LeaveTypeRule country(Country country) {
        this.country = country;
        return this;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LeaveTypeRule leaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
        return this;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public BigDecimal getDeductionPercentage() {
        return deductionPercentage;
    }

    public LeaveTypeRule deductionPercentage(BigDecimal deductionPercentage) {
        this.deductionPercentage = deductionPercentage;
        return this;
    }

    public void setDeductionPercentage(BigDecimal deductionPercentage) {
        this.deductionPercentage = deductionPercentage;
    }

    public Boolean getAffectsTaxableSalary() {
        return affectsTaxableSalary;
    }

    public LeaveTypeRule affectsTaxableSalary(Boolean affectsTaxableSalary) {
        this.affectsTaxableSalary = affectsTaxableSalary;
        return this;
    }

    public void setAffectsTaxableSalary(Boolean affectsTaxableSalary) {
        this.affectsTaxableSalary = affectsTaxableSalary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveTypeRule that = (LeaveTypeRule) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "LeaveTypeRule{" +
            "id=" +
            id +
            ", country=" +
            (country != null ? country.getCode() : null) +
            ", leaveType=" +
            leaveType +
            ", deductionPercentage=" +
            deductionPercentage +
            ", affectsTaxableSalary=" +
            affectsTaxableSalary +
            '}'
        );
    }
}
