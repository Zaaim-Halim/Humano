package com.humano.domain.payroll;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * ExchangeRate represents the conversion rate between two currencies on a specific date.
 * <p>
 * It is used for currency conversions in multi-currency payroll calculations, financial reporting,
 * and international compensation management. Each exchange rate defines the value of one currency
 * in terms of another at a specific point in time.
 * <ul>
 *   <li><b>fromCcy</b>: The source currency being converted from.</li>
 *   <li><b>toCcy</b>: The target currency being converted to.</li>
 *   <li><b>date</b>: The effective date of this exchange rate.</li>
 *   <li><b>rate</b>: The conversion rate (how much of toCcy equals 1 unit of fromCcy).</li>
 * </ul>
 * <p>
 * Exchange rates are essential for organizations operating in multiple countries with different
 * currencies, enabling accurate payroll calculations and financial consolidation.
 */
@Entity
@Table(name = "exchange_rate")
public class ExchangeRate extends AbstractAuditingEntity<UUID> {

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
     * The source currency being converted from.
     * <p>
     * Represents the base currency in the exchange rate calculation.
     */
    @ManyToOne
    @JoinColumn(name = "from_currency_id", nullable = false)
    @NotNull(message = "Source currency is required")
    private Currency fromCcy;

    /**
     * The target currency being converted to.
     * <p>
     * Represents the quote currency in the exchange rate calculation.
     */
    @ManyToOne
    @JoinColumn(name = "to_currency_id", nullable = false)
    @NotNull(message = "Target currency is required")
    private Currency toCcy;

    /**
     * The effective date of this exchange rate.
     * <p>
     * Defines when this exchange rate is valid for use in calculations.
     */
    @Column(name = "date", nullable = false)
    @NotNull(message = "Date is required")
    private LocalDate date;

    /**
     * The conversion rate between the two currencies.
     * <p>
     * Represents how much of the target currency equals 1 unit of the source currency.
     * Must be positive.
     */
    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.000001", inclusive = true, message = "Rate must be positive")
    private BigDecimal rate;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Currency getFromCcy() {
        return fromCcy;
    }

    public ExchangeRate fromCcy(Currency fromCcy) {
        this.fromCcy = fromCcy;
        return this;
    }

    public void setFromCcy(Currency fromCcy) {
        this.fromCcy = fromCcy;
    }

    public Currency getToCcy() {
        return toCcy;
    }

    public ExchangeRate toCcy(Currency toCcy) {
        this.toCcy = toCcy;
        return this;
    }

    public void setToCcy(Currency toCcy) {
        this.toCcy = toCcy;
    }

    public LocalDate getDate() {
        return date;
    }

    public ExchangeRate date(LocalDate date) {
        this.date = date;
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public ExchangeRate rate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeRate that = (ExchangeRate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "ExchangeRate{" +
            "id=" +
            id +
            ", fromCcy=" +
            (fromCcy != null ? fromCcy.getCode() : null) +
            ", toCcy=" +
            (toCcy != null ? toCcy.getCode() : null) +
            ", date=" +
            date +
            ", rate=" +
            rate +
            '}'
        );
    }
}
