package com.humano.domain.tenant;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.UUID;

/**
 * Represents an organization within a tenant.
 * <p>
 * The Organization entity models a business unit, division, or company that belongs to a Tenant. It is used to group employees, departments, and other HR/payroll structures under a single logical entity. Organizations are essential for multi-tenant and multi-organization scenarios, allowing for separation of data and business logic per organization.
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
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    public UUID getId() {
        return id;
    }

    // getters and setters
}
