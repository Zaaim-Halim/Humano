package com.humano.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.Objects;
import java.util.UUID;

/**
 * Currency entity represents a monetary unit used for financial transactions and payroll.
 * <p>
 * This entity stores information about different currencies used throughout the system,
 * including their ISO codes, full names, and symbols. It supports multi-currency payroll,
 * international compensation, and financial reporting across different regions.
 * <ul>
 *   <li><b>code</b>: The ISO 4217 currency code (e.g., "EUR", "USD", "GBP").</li>
 *   <li><b>name</b>: The full name of the currency (e.g., "Euro", "US Dollar").</li>
 *   <li><b>symbol</b>: The currency symbol used for display purposes (e.g., "€", "$").</li>
 * </ul>
 * <p>
 * Currency is a foundational entity for financial operations and is referenced by
 * various payroll-related entities such as Compensation, ExchangeRate, and PayrollResult.
 */
@Entity
@Table(name = "currency")
public class Currency extends AbstractAuditingEntity<UUID> {
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
     * The ISO 4217 currency code.
     * <p>
     * A three-letter code that uniquely identifies the currency (e.g., "EUR", "USD").
     * This is the standard international identifier for the currency.
     */
    @Column(name = "code", nullable = false, unique = true, length = 3)
    @NotNull(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String code;

    /**
     * The full name of the currency.
     * <p>
     * The official name of the currency in English (e.g., "Euro", "US Dollar").
     * Used for display and reporting purposes.
     */
    @Column(name = "name", nullable = false)
    @NotNull(message = "Currency name is required")
    @Size(min = 2, max = 100, message = "Currency name must be between 2 and 100 characters")
    private String name;

    /**
     * The currency symbol.
     * <p>
     * The symbol used to represent the currency in financial displays (e.g., "€", "$").
     * Optional as some currencies may not have a widely recognized symbol.
     */
    @Column(name = "symbol", length = 5)
    @Size(max = 5, message = "Currency symbol cannot exceed 5 characters")
    private String symbol;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public Currency code(String code) {
        this.code = code;
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public Currency name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public Currency symbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Currency currency = (Currency) o;
        return Objects.equals(id, currency.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Currency{" +
            "id=" + id +
            ", code='" + code + '\'' +
            ", name='" + name + '\'' +
            ", symbol='" + symbol + '\'' +
            '}';
    }
}
