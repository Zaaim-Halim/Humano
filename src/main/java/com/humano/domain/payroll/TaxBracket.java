package com.humano.domain.payroll;

import com.humano.domain.enumeration.payroll.TaxBracketBasis;
import com.humano.domain.enumeration.payroll.TaxCode;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Country;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Represents a tax bracket for a specific tax code and country.
 * <p>
 * Tax brackets define the progressive taxation structure where different income ranges
 * are taxed at different rates. Each bracket specifies the income range it applies to
 * and the marginal rate for that range.
 * <p>
 * Tax brackets are used in payroll calculations to accurately compute income tax
 * withholdings based on employee earnings.
 * <ul>
 *   <li><b>lower</b>: Minimum income for this bracket.</li>
 *   <li><b>upper</b>: Maximum income for this bracket.</li>
 *   <li><b>rate</b>: Marginal rate applied to the slice of income between `lower` and `upper`.</li>
 *   <li><b>fixedPart</b>: Flat amount added once when income reaches this bracket (usually 0).</li>
 *   <li><b>basis</b>: Whether the thresholds are annual or per pay period.</li>
 *   <li><b>taxCode</b>: Identifier for the tax type (e.g., PIT, VAT, Corporate Tax).</li>
 * </ul>
 * <p>
 * Each bracket is valid for a specific period and country. The brackets active for a
 * (country, taxCode) on a date form one set, which must start at 0, be contiguous, and
 * share a single basis — see {@code TaxCalculationService#validateBracketSet}.
 */
@Entity
@Table(name = "tax_bracket")
public class TaxBracket extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
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
     * The rate applied to income in this bracket, as a fraction (0.2 = 20%).
     * Must be between 0% and 100%, with at most 6 decimals (e.g. 0.03078 for 3.078%).
     */
    @Column(name = "rate", nullable = false, precision = 7, scale = 6)
    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Rate cannot be negative")
    @DecimalMax(value = "1.0", inclusive = true, message = "Rate cannot exceed 100%")
    private BigDecimal rate;

    /**
     * Flat amount added once when taxable income reaches this bracket.
     * <p>
     * This is NOT the cumulative tax of the lower brackets: the calculation already sums
     * every lower slice, so a table published as "tax = fixed amount + rate × (income − lower)"
     * must be seeded with {@code fixedPart = 0} on every row. Leave it 0 unless the
     * jurisdiction charges a genuine per-band flat amount.
     */
    @Column(name = "fixed_part", precision = 19, scale = 6)
    @DecimalMin(value = "0.0", inclusive = true, message = "Fixed part cannot be negative")
    private BigDecimal fixedPart = BigDecimal.ZERO;

    /**
     * Whether {@link #lower} / {@link #upper} are annual or per-pay-period amounts.
     * <p>
     * Existing rows default to {@link TaxBracketBasis#PER_PERIOD}, the original behaviour.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 20)
    @NotNull(message = "Basis is required")
    private TaxBracketBasis basis = TaxBracketBasis.PER_PERIOD;

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

    public TaxBracketBasis getBasis() {
        return basis;
    }

    public TaxBracket basis(TaxBracketBasis basis) {
        this.basis = basis;
        return this;
    }

    public void setBasis(TaxBracketBasis basis) {
        this.basis = basis;
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
        return (
            "TaxBracket{" +
            "id=" +
            id +
            ", taxCode=" +
            taxCode +
            ", country=" +
            (country != null ? country.getCode() : null) +
            ", lower=" +
            lower +
            ", upper=" +
            upper +
            ", rate=" +
            rate +
            ", basis=" +
            basis +
            ", validFrom=" +
            validFrom +
            ", validTo=" +
            validTo +
            '}'
        );
    }
}
