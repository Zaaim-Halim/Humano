package com.humano.domain.billing;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Master-DB currency reference used for billing artifacts (invoices, payments, plans, coupons).
 * <p>
 * Mirrors the tenant-DB {@code com.humano.domain.payroll.Currency} but lives in the master
 * persistence unit so that {@link Payment} and friends do not cross the master/tenant boundary
 * (see ROADMAP invariant I1). Maps to the {@code billing_currency} table.
 */
@Entity
@Table(name = "billing_currency")
public class BillingCurrency extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 3)
    @NotNull(message = "Currency code is required")
    @Enumerated(EnumType.STRING)
    private CurrencyCode code;

    @Column(name = "name", nullable = false)
    @NotNull(message = "Currency name is required")
    @Size(min = 2, max = 100, message = "Currency name must be between 2 and 100 characters")
    private String name;

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

    public CurrencyCode getCode() {
        return code;
    }

    public void setCode(CurrencyCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BillingCurrency other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BillingCurrency{id=" + id + ", code=" + code + ", name='" + name + "', symbol='" + symbol + "'}";
    }
}
