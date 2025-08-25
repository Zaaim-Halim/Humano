package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.billing.SubscriptionType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Represents a subscription plan offered to tenants.
 * <p>
 * A SubscriptionPlan defines the available features and pricing for a given subscription tier.
 * It includes the plan name (as an enum), price, and a list of associated features.
 * This entity extends AbstractAuditingEntity for auditing purposes.
 * </p>
 * <ul>
 *   <li><b>id</b>: Unique identifier for the subscription plan.</li>
 *   <li><b>name</b>: The subscription tier (e.g., FREE, PREMIUM).</li>
 *   <li><b>price</b>: The cost of the plan, stored as BigDecimal.</li>
 *   <li><b>features</b>: List of features included in the plan.</li>
 * </ul>
 */
@Entity
public class SubscriptionPlan extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false)
    private SubscriptionType subscriptionType;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "subscription_plan_id")
    private List<Feature> features;

    public UUID getId() { return id; }

}
