package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;

import java.util.Objects;
import java.util.UUID;

/**
 * Feature entity represents a capability or module available in subscription plans.
 * <p>
 * This entity defines a specific functionality that can be included in subscription plans
 * and made available to tenants. Features are the building blocks of plan tiers and control
 * what capabilities tenants can access within the HR and payroll system.
 * <ul>
 *   <li><b>name</b>: Display name of the feature (e.g., "Payroll Processing", "HR Analytics").</li>
 *   <li><b>description</b>: Detailed explanation of what the feature provides.</li>
 *   <li><b>code</b>: Unique programmatic identifier used for feature detection in code.</li>
 *   <li><b>category</b>: Grouping category for UI organization (e.g., "HR", "Payroll", "Reporting").</li>
 *   <li><b>active</b>: Whether this feature is currently available for inclusion in plans.</li>
 * </ul>
 * <p>
 * Features are associated with subscription plans to define what capabilities are included
 * in each plan tier, controlling tenant access to system functionality.
 */
@Entity
@Table(name = "billing_feature")
public class Feature extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @org.hibernate.annotations.Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Display name of the feature.
     * <p>
     * Human-readable name used in the UI and marketing materials
     * (e.g., "Payroll Processing", "HR Analytics").
     */
    @Column(name = "name", nullable = false)
    @NotBlank(message = "Feature name is required")
    @Size(min = 2, max = 100, message = "Feature name must be between 2 and 100 characters")
    private String name;

    /**
     * Detailed explanation of the feature.
     * <p>
     * Provides a comprehensive description of what capabilities
     * this feature provides to tenants.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    @Size(max = 4000, message = "Description cannot exceed 4000 characters")
    private String description;

    /**
     * Unique programmatic identifier.
     * <p>
     * Used for feature detection in code and as a stable reference
     * that doesn't change when the display name changes.
     */
    @Column(name = "code", nullable = false, unique = true)
    @NotBlank(message = "Feature code is required")
    @Size(min = 2, max = 50, message = "Feature code must be between 2 and 50 characters")
    private String code;

    /**
     * Grouping category for UI organization.
     * <p>
     * Used to group related features together in the UI
     * (e.g., "HR", "Payroll", "Reporting").
     */
    @Column(name = "category")
    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;

    /**
     * Whether this feature is currently available.
     * <p>
     * When false, the feature is hidden from new plan configurations
     * but existing plans containing this feature continue to function.
     */
    @Column(name = "active", nullable = false)
    @NotNull(message = "Active status is required")
    private boolean active = true;


    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Feature name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public Feature description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public Feature code(String code) {
        this.code = code;
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCategory() {
        return category;
    }

    public Feature category(String category) {
        this.category = category;
        return this;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isActive() {
        return active;
    }

    public Feature active(boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(boolean active) {
        this.active = active;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Feature feature = (Feature) o;
        return Objects.equals(id, feature.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Feature{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", code='" + code + '\'' +
            ", category='" + category + '\'' +
            ", active=" + active +
            '}';
    }
}
