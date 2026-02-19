package com.humano.domain.payroll;

import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * TaxWithholding entity represents tax-specific deductions from employee pay.
 * <p>
 * This tracks different types of tax withholdings (income tax, social security, etc.), their rates,
 * and related tax information. Used for calculating employee and employer tax obligations.
 * <p>
 * Tax withholdings are a critical component of payroll processing that must comply with
 * various government regulations. They affect both the employee's net pay and the employer's
 * tax reporting and payment obligations. Different tax types may have different calculation
 * rules and reporting requirements.
 */
@Entity
@Table(name = "tax_withholding")
public class TaxWithholding extends AbstractAuditingEntity<UUID> {

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
     * The employee whose taxes are being withheld.
     * <p>
     * Links to the Employee entity in the HR domain to maintain
     * relationship between tax withholdings and employees.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The type of tax being withheld (INCOME, SOCIAL_SECURITY, MEDICARE, etc.).
     * <p>
     * Categorizes the tax for reporting, processing, and compliance purposes.
     * Different tax types have different calculation rules and reporting requirements.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TaxType type;

    /**
     * The tax withholding rate as a percentage.
     * <p>
     * Used to calculate the amount of tax to withhold from employee pay.
     * May vary based on employee income, filing status, and other factors.
     * <p>
     * Minimum value is 0% and maximum is 100%, as tax rates cannot exceed total pay.
     */
    @Column(name = "rate", nullable = false)
    @NotNull(message = "Tax rate is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Tax rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100%")
    private BigDecimal rate;

    /**
     * The date from which this tax withholding becomes effective.
     * <p>
     * Used to track when a tax withholding should start being applied to payroll calculations.
     * Important for handling tax rate changes during a fiscal year.
     */
    @Column(name = "effective_from", nullable = false)
    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    /**
     * The date until which this tax withholding is effective.
     * <p>
     * Optional field used to track when a tax withholding should no longer be applied.
     * If null, the withholding is considered ongoing until explicitly terminated.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * The government authority to which the tax is paid.
     * <p>
     * Identifies the tax jurisdiction (e.g., IRS, state tax authority).
     * Essential for proper tax reporting and remittance.
     */
    @Column(name = "tax_authority", nullable = false)
    @NotNull(message = "Tax authority is required")
    @Size(min = 2, max = 100, message = "Tax authority name must be between 2 and 100 characters")
    private String taxAuthority;

    /**
     * Any specific tax identifier or reference code.
     * <p>
     * May include tax account numbers, registration IDs, or other
     * jurisdiction-specific identifiers for tax reporting.
     */
    @Column(name = "tax_identifier")
    @Size(max = 50, message = "Tax identifier cannot exceed 50 characters")
    private String taxIdentifier;

    /**
     * Running total of tax withheld in the current tax year.
     * <p>
     * Used to track accumulated tax withholdings for reporting purposes
     * and to manage tax caps or limits that apply to certain tax types.
     * <p>
     * Default value is 0, as new tax withholding records start with no accumulated amount.
     */
    @Column(name = "year_to_date_amount")
    @DecimalMin(value = "0.0", inclusive = true, message = "Year-to-date amount cannot be negative")
    private BigDecimal yearToDateAmount = BigDecimal.ZERO;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public TaxWithholding employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public TaxType getType() {
        return type;
    }

    public TaxWithholding type(TaxType type) {
        this.type = type;
        return this;
    }

    public void setType(TaxType type) {
        this.type = type;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public TaxWithholding rate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public TaxWithholding effectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
        return this;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public TaxWithholding effectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
        return this;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getTaxAuthority() {
        return taxAuthority;
    }

    public TaxWithholding taxAuthority(String taxAuthority) {
        this.taxAuthority = taxAuthority;
        return this;
    }

    public void setTaxAuthority(String taxAuthority) {
        this.taxAuthority = taxAuthority;
    }

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    public TaxWithholding taxIdentifier(String taxIdentifier) {
        this.taxIdentifier = taxIdentifier;
        return this;
    }

    public void setTaxIdentifier(String taxIdentifier) {
        this.taxIdentifier = taxIdentifier;
    }

    public BigDecimal getYearToDateAmount() {
        return yearToDateAmount;
    }

    public TaxWithholding yearToDateAmount(BigDecimal yearToDateAmount) {
        this.yearToDateAmount = yearToDateAmount;
        return this;
    }

    public void setYearToDateAmount(BigDecimal yearToDateAmount) {
        this.yearToDateAmount = yearToDateAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaxWithholding that = (TaxWithholding) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "TaxWithholding{" +
            "id=" +
            id +
            ", type=" +
            type +
            ", rate=" +
            rate +
            ", effectiveFrom=" +
            effectiveFrom +
            ", effectiveTo=" +
            effectiveTo +
            ", taxAuthority='" +
            taxAuthority +
            '\'' +
            ", taxIdentifier='" +
            taxIdentifier +
            '\'' +
            ", yearToDateAmount=" +
            yearToDateAmount +
            '}'
        );
    }
}
