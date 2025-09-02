package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Country;
import com.humano.domain.enumeration.payroll.TaxCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a tax bracket for a specific tax code and country.
 * <p>
 * Tax brackets define the progressive taxation structure where different income ranges
 * are taxed at different rates. Each bracket specifies the income range it applies to,
 * the tax rate for that range, and the fixed amount from previous brackets.
 * <p>
 * Tax brackets are used in payroll calculations to accurately compute income tax
 * withholdings based on employee earnings.
 * <ul>
 *   <li><b>lower</b>: Minimum income for this bracket.</li>
 *   <li><b>upper</b>: Maximum income for this bracket.</li>
 *   <li><b>rate</b>: Tax rate applied to the taxable amount above `lower`.</li>
 *   <li><b>fixedPart</b>: Fixed tax amount from previous brackets.</li>
 *   <li><b>taxCode</b>: Identifier for the tax type (e.g., PIT, VAT, Corporate Tax).</li>
 * </ul>
 * <p>
 * Each bracket is valid for a specific period and country.
 */
@Entity
@Table(name = "tax_bracket")
public class TaxBracket extends AbstractAuditingEntity<UUID> {
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

    /**
     * The country this tax bracket applies to.
     * <p>
     * Tax brackets are country-specific due to different taxation systems.
     */
    @ManyToOne
    @JoinColumn(name = "country_id", nullable = false)
    @NotNull(message = "Country is required")
    private Country country;

    /**
     * The date from which this tax bracket becomes effective.
     * <p>
     * Used to handle changes in tax laws over time.
     */
    @Column(name = "valid_from", nullable = false)
    @NotNull(message = "Valid from date is required")
    private LocalDate validFrom;

    /**
     * The date until which this tax bracket is effective.
     * <p>
     * If null, the bracket is considered currently valid until explicitly ended.
     */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /**
     * Minimum income for this bracket.
     * <p>
     * The lower bound of the income range to which this bracket applies.
     * Must be non-negative.
     */
    @Column(name = "lower", nullable = false, precision = 19, scale = 6)
    @NotNull(message = "Lower bound is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Lower bound cannot be negative")
    private BigDecimal lower;

    /**
     * Maximum income for this bracket.
     * <p>
     * The upper bound of the income range to which this bracket applies.
     * Must be greater than the lower bound.
     */
    @Column(name = "upper", nullable = false, precision = 19, scale = 6)
    @NotNull(message = "Upper bound is required")
    private BigDecimal upper;

    /**
     * Tax rate applied to the taxable amount above the lower bound.
     * <p>
     * The percentage rate applied to income in this bracket.
     * Must be between 0% and 100%.
     */
    @Column(name = "rate", nullable = false, precision = 5, scale = 4)
    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Rate cannot be negative")
    @DecimalMax(value = "1.0", inclusive = true, message = "Rate cannot exceed 100%")
    private BigDecimal rate;

    /**
     * Fixed tax amount from previous brackets.
     * <p>
     * The accumulated tax amount from all lower brackets.
     * Used for optimizing tax calculations.
     */
    @Column(name = "fixed_part", precision = 19, scale = 6)
    @DecimalMin(value = "0.0", inclusive = true, message = "Fixed part cannot be negative")
    private BigDecimal fixedPart = BigDecimal.ZERO;

    /**
     * Identifier for the tax type (e.g., PIT, VAT, Corporate Tax).
     * <p>
     * Categorizes the tax bracket for different types of taxation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_code", nullable = false)
    @NotNull(message = "Tax code is required")
    private TaxCode taxCode;

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

    public TaxBracket country(Country country) {
        this.country = country;
        return this;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public TaxBracket validFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
        return this;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public TaxBracket validTo(LocalDate validTo) {
        this.validTo = validTo;
        return this;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public BigDecimal getLower() {
        return lower;
    }

    public TaxBracket lower(BigDecimal lower) {
        this.lower = lower;
        return this;
    }

    public void setLower(BigDecimal lower) {
        this.lower = lower;
    }

    public BigDecimal getUpper() {
        return upper;
    }

    public TaxBracket upper(BigDecimal upper) {
        this.upper = upper;
        return this;
    }

    public void setUpper(BigDecimal upper) {
        this.upper = upper;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public TaxBracket rate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getFixedPart() {
        return fixedPart;
    }

    public TaxBracket fixedPart(BigDecimal fixedPart) {
        this.fixedPart = fixedPart;
        return this;
    }

    public void setFixedPart(BigDecimal fixedPart) {
        this.fixedPart = fixedPart;
    }

    public TaxCode getTaxCode() {
        return taxCode;
    }

    public TaxBracket taxCode(TaxCode taxCode) {
        this.taxCode = taxCode;
        return this;
    }

    public void setTaxCode(TaxCode taxCode) {
        this.taxCode = taxCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaxBracket that = (TaxBracket) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TaxBracket{" +
            "id=" + id +
            ", taxCode=" + taxCode +
            ", country=" + (country != null ? country.getCode() : null) +
            ", lower=" + lower +
            ", upper=" + upper +
            ", rate=" + rate +
            ", validFrom=" + validFrom +
            ", validTo=" + validTo +
            '}';
    }
}
