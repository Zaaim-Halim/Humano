package com.humano.domain.billing;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Packs of a plan feature bought on top of a subscription — e.g. 2 packs of 100 GB of storage. They
 * raise that feature's limit by {@code quantity × unitSize} and are billed {@code quantity × unitPrice}
 * a month on each renewal. Size and price are snapshotted when bought. One row per feature.
 */
@Entity
@Table(name = "billing_subscription_addon")
public class SubscriptionAddon extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    /** The plan feature the packs add to ({@code FeatureCodes}). */
    @Column(name = "feature_code", nullable = false, length = 50)
    @NotNull
    private String featureCode;

    @Column(name = "quantity", nullable = false)
    @Min(0)
    private int quantity;

    /** What one pack adds, in the feature's unit (GB for storage). */
    @Column(name = "unit_size", nullable = false)
    @Min(1)
    private int unitSize;

    /** Monthly price of one pack, in the billing currency. */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal unitPrice;

    /**
     * How far the packs are billed, when they get their own monthly invoice (yearly subscriptions);
     * with a monthly subscription they are billed on its renewal invoice instead.
     */
    @Column(name = "billed_until")
    private java.time.Instant billedUntil;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getUnitSize() {
        return unitSize;
    }

    public void setUnitSize(int unitSize) {
        this.unitSize = unitSize;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public java.time.Instant getBilledUntil() {
        return billedUntil;
    }

    public void setBilledUntil(java.time.Instant billedUntil) {
        this.billedUntil = billedUntil;
    }

    /** What the packs add to the feature's limit. */
    public int addedAmount() {
        return quantity * unitSize;
    }

    /** What the packs cost per month. */
    public BigDecimal monthlyPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof SubscriptionAddon other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "SubscriptionAddon{id=" + id + ", featureCode='" + featureCode + "', quantity=" + quantity + "}";
    }
}
