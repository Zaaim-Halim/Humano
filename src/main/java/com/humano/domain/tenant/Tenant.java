package com.humano.domain.tenant;

import com.humano.converters.TimeZoneConverter;
import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.billing.SubscriptionPlan;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Represents a tenant (organization or company) in the system.
 * <p>
 * The Tenant entity stores information about a subscribing organization, including its name, domain, subdomain, logo, and timezone.
 * It extends AbstractAuditingEntity for auditing purposes and supports multi-tenancy.
 * </p>
 * <ul>
 *   <li><b>id</b>: Unique identifier for the tenant.</li>
 *   <li><b>name</b>: The display name of the tenant.</li>
 *   <li><b>domain</b>: The main domain for the tenant (must be unique).</li>
 *   <li><b>subdomain</b>: The subdomain for tenant-specific access (must be unique).</li>
 *   <li><b>logo</b>: URL or path to the tenant's logo.</li>
 *   <li><b>timezone</b>: The tenant's preferred timezone.</li>
 * </ul>
 */
@Entity
public class Tenant extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "domain", unique = true, nullable = false)
    private String domain;
    @Column(name = "subdomain", nullable = false, unique = true)
    private String subdomain;

    @Column(name = "logo")
    private String logo;

    @Column(name = "timezone")
    @Convert(converter = TimeZoneConverter.class)
    private TimeZone timezone = TimeZone.getTimeZone("UTC"); // Default UTC

    @Lob
    @Column(name = "booking_policies")
    private String bookingPolicies;

    @Lob
    @Column(name = "hr_policies")
    private String hrPolicies;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @OneToMany(mappedBy = "tenant")
    private Set<Organization> organizations;

    @Override
    public UUID getId() {
        return id;
    }

    // getters and setters
}
