package com.humano.domain.billing;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Per-country VAT / sales-tax / GST rate applied to billing invoices (P4.1).
 *
 * <p>Rows are stored in the <strong>master DB</strong> alongside the rest of the billing
 * aggregate. {@link com.humano.service.billing.BillingTaxResolver} looks up the active
 * row for a tenant's country at invoice-issuance time; the resolved rate is then
 * persisted on the invoice ({@code invoice.tax_rate} + {@code invoice.tax_amount}) so a
 * future rate change doesn't retroactively shift already-issued invoices.
 *
 * <p>Historical tracking is supported via the {@code validFrom} / {@code validTo} window:
 * when a country's VAT changes, the operator inserts a new row with
 * {@code validFrom = newRate's effective date} and updates the prior row's {@code validTo}
 * to the day before. The unique constraint on {@code (country_code, valid_from)} prevents
 * two open-ended rows for the same country.
 */
@Entity
@Table(name = "country_tax_rate")
public class CountryTaxRate extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** ISO 3166-1 alpha-2 country code (e.g. {@code FR}, {@code DE}, {@code US}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "country_code", nullable = false, length = 2)
    @NotNull
    private CountryCode countryCode;

    /** Human-readable tax name, e.g. {@code VAT}, {@code GST}, {@code Sales Tax}. Used on invoice line text. */
    @Column(name = "tax_name", nullable = false)
    @NotNull
    @Size(max = 50)
    private String taxName;

    /** Rate as a decimal ratio (0..1). 0.2000 means 20%. */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "1.0", inclusive = true)
    private BigDecimal taxRate;

    /** First date on which this rate applies (inclusive). */
    @Column(name = "valid_from", nullable = false)
    @NotNull
    private LocalDate validFrom;

    /** Last date on which this rate applies (inclusive). {@code null} = currently active. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CountryCode getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(CountryCode countryCode) {
        this.countryCode = countryCode;
    }

    public String getTaxName() {
        return taxName;
    }

    public void setTaxName(String taxName) {
        this.taxName = taxName;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CountryTaxRate that = (CountryTaxRate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "CountryTaxRate{id=" +
            id +
            ", countryCode=" +
            countryCode +
            ", taxName='" +
            taxName +
            "', taxRate=" +
            taxRate +
            ", validFrom=" +
            validFrom +
            ", validTo=" +
            validTo +
            '}'
        );
    }
}
