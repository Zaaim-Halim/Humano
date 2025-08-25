package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * Represents a feature available in a subscription plan.
 * <p>
 * The Feature entity defines a specific capability or module that can be included in a SubscriptionPlan. Features are used to differentiate between subscription tiers and control access to system functionality for tenants.
 * </p>
 * <ul>
 *   <li><b>id</b>: Unique identifier for the feature.</li>
 *   <li><b>name</b>: The name of the feature (e.g., "Payroll", "HR Analytics").</li>
 *   <li><b>description</b>: A human-readable description of the feature.</li>
 * </ul>
 */
@Entity
public class Feature extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    // Getters and Setters
    public UUID getId() { return id; }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
