package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.billing.SubscriptionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * SubscriptionPlan entity represents a service tier offered to tenants.
 * <p>
 * This entity defines a pricing tier within the HR and payroll system, including
 * its features and pricing structure. Subscription plans are the core product offerings
 * that tenants can subscribe to, each with different capabilities and price points.
 * <ul>
 *   <li><b>subscriptionType</b>: The tier category (e.g., FREE, BASIC, PREMIUM, ENTERPRISE).</li>
 *   <li><b>price</b>: The standard price for this plan.</li>
 *   <li><b>displayName</b>: Human-readable name for the plan used in the UI and marketing.</li>
 *   <li><b>active</b>: Whether this plan is currently available for new subscriptions.</li>
 *   <li><b>basePrice</b>: Optional refined base price that may replace the standard price.</li>
 *   <li><b>features</b>: Set of features included in this plan.</li>
 * </ul>
 * <p>
 * Subscription plans serve as templates for creating subscriptions when tenants sign up,
 * determining which features they have access to and how much they are billed.
 */
@Entity
@Table(name = "billing_subscription_plan")
public class SubscriptionPlan extends AbstractAuditingEntity<UUID> {
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
     * The tier category of this subscription plan.
     * <p>
     * Categorizes the plan into a specific tier (e.g., FREE, BASIC, PREMIUM, ENTERPRISE).
     * This is used for plan comparison and determining service levels.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false)
    @NotNull(message = "Subscription type is required")
    private SubscriptionType subscriptionType;

    /**
     * The standard price for this plan.
     * <p>
     * The default cost of the subscription plan before any customizations or discounts.
     * For plans with multiple billing cycles, this typically represents the monthly price.
     */
    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    private BigDecimal price;

    /**
     * Human-readable name for the plan.
     * <p>
     * Used for display in the UI, invoices, and marketing materials.
     * May include spaces and special characters, unlike the enumerated subscriptionType.
     */
    @Column(name = "display_name")
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    private String displayName;

    /**
     * Whether this plan is currently available for new subscriptions.
     * <p>
     * When false, the plan is hidden from new sign-ups but existing subscriptions
     * continue to function.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Optional refined base price.
     * <p>
     * Can be used to override the standard price for special promotions
     * or custom pricing. If null, the standard price is used.
     */
    @Column(name = "base_price", precision = 19, scale = 4)
    @DecimalMin(value = "0.0", inclusive = true, message = "Base price cannot be negative")
    private BigDecimal basePrice;

    /**
     * Set of features included in this plan.
     * <p>
     * Defines the capabilities and modules available to subscribers of this plan.
     * Features determine what functionality tenants can access in the HR and payroll system.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "subscription_plan_id")
    private Set<Feature> features = new HashSet<>();

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public SubscriptionPlan subscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
        return this;
    }

    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public SubscriptionPlan price(BigDecimal price) {
        this.price = price;
        return this;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SubscriptionPlan displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return active;
    }

    public SubscriptionPlan active(boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BigDecimal getBasePrice() {
        return basePrice != null ? basePrice : price;
    }

    public SubscriptionPlan basePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
        return this;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Set<Feature> getFeatures() {
        return features;
    }

    public SubscriptionPlan features(Set<Feature> features) {
        this.features = features;
        return this;
    }

    public void setFeatures(Set<Feature> features) {
        this.features = features;
    }

    public SubscriptionPlan addFeature(Feature feature) {
        this.features.add(feature);
        return this;
    }

    public SubscriptionPlan removeFeature(Feature feature) {
        this.features.remove(feature);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriptionPlan that = (SubscriptionPlan) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SubscriptionPlan{" +
            "id=" + id +
            ", subscriptionType=" + subscriptionType +
            ", price=" + price +
            ", displayName='" + displayName + '\'' +
            ", active=" + active +
            ", basePrice=" + basePrice +
            ", features=" + features.size() +
            '}';
    }
}
