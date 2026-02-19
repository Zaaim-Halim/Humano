package com.humano.domain.tenant;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Represents an organization within a tenant.
 * <p>
 * The Organization entity models a business unit, division, or company that belongs to a Tenant.
 * It is used to group employees, departments, and other HR/payroll structures under a single
 * logical entity. Organizations are essential for multi-tenant and multi-organization scenarios,
 * allowing for separation of data and business logic per organization.
 * </p>
 * <ul>
 *   <li><b>id</b>: Unique identifier for the organization.</li>
 *   <li><b>name</b>: The display name of the organization.</li>
 *   <li><b>tenant</b>: Reference to the parent Tenant entity.</li>
 * </ul>
 */
@Entity
@Table(name = "organization")
public class Organization extends AbstractAuditingEntity<UUID> {

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
     * The display name of the organization.
     * <p>
     * This is the official name of the business unit, division, or department.
     * </p>
     */
    @Column(name = "name", nullable = false)
    @NotBlank(message = "Organization name is required")
    @Size(min = 2, max = 100, message = "Organization name must be between 2 and 100 characters")
    private String name;

    /**
     * Reference to the parent Tenant entity.
     * <p>
     * Each organization must belong to exactly one tenant.
     * </p>
     */
    @ManyToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    @NotNull(message = "Tenant is required")
    private Tenant tenant;

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

    public Organization name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Organization tenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Organization that = (Organization) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Organization{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}
