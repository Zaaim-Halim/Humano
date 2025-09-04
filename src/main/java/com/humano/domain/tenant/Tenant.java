package com.humano.domain.tenant;

import com.humano.converters.TimeZoneConverter;
import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.billing.SubscriptionPlan;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.HashSet;
import java.util.Objects;
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
 *   <li><b>bookingPolicies</b>: Text describing the tenant's booking policies.</li>
 *   <li><b>hrPolicies</b>: Text describing the tenant's HR policies.</li>
 *   <li><b>subscriptionPlan</b>: The subscription plan associated with this tenant.</li>
 *   <li><b>organizations</b>: Set of organizations belonging to this tenant.</li>
 * </ul>
 */
@Entity
@Table(name = "tenant")
public class Tenant extends AbstractAuditingEntity<UUID> {
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
     * The display name of the tenant.
     * <p>
     * This is the official name of the company or organization.
     */
    @Column(name = "name", nullable = false)
    @NotBlank(message = "Tenant name is required")
    @Size(min = 2, max = 100, message = "Tenant name must be between 2 and 100 characters")
    private String name;

    /**
     * The main domain for the tenant.
     * <p>
     * Must be unique across all tenants in the system.
     * Usually represents the company's primary website domain.
     */
    @Column(name = "domain", unique = true, nullable = false)
    @NotBlank(message = "Domain is required")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}$", message = "Domain must be a valid domain name")
    private String domain;

    /**
     * The subdomain for tenant-specific access.
     * <p>
     * Must be unique across all tenants in the system.
     * Used to create tenant-specific URLs for accessing the system.
     */
    @Column(name = "subdomain", nullable = false, unique = true)
    @NotBlank(message = "Subdomain is required")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$", message = "Subdomain must contain only letters, numbers, and hyphens, and cannot start or end with a hyphen")
    @Size(min = 2, max = 63, message = "Subdomain must be between 2 and 63 characters")
    private String subdomain;

    /**
     * URL or path to the tenant's logo.
     * <p>
     * Optional field that stores the location of the tenant's brand logo,
     * which can be displayed in the UI.
     */
    @Column(name = "logo")
    private String logo;

    /**
     * The tenant's preferred timezone.
     * <p>
     * Determines the default timezone for displaying dates and times.
     * Defaults to UTC if not specified.
     */
    @Column(name = "timezone")
    @Convert(converter = TimeZoneConverter.class)
    @NotNull(message = "Timezone is required")
    private TimeZone timezone = TimeZone.getTimeZone("UTC"); // Default UTC

    /**
     * Text describing the tenant's booking policies.
     * <p>
     * Contains information about how resources should be booked,
     * approval workflows, and other booking-related policies.
     */
    @Lob
    @Column(name = "booking_policies", columnDefinition = "TEXT")
    private String bookingPolicies;

    /**
     * Text describing the tenant's HR policies.
     * <p>
     * Contains information about employee management, benefits,
     * time off, and other HR-related policies.
     */
    @Lob
    @Column(name = "hr_policies", columnDefinition = "TEXT")
    private String hrPolicies;

    /**
     * The subscription plan associated with this tenant.
     * <p>
     * Defines the features, limits, and billing information for this tenant.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    @NotNull(message = "Subscription plan is required")
    private SubscriptionPlan subscriptionPlan;

    /**
     * Set of organizations belonging to this tenant.
     * <p>
     * Represents the organizational structure within the tenant,
     * allowing for multiple business units or divisions.
     */
    @OneToMany(mappedBy = "tenant")
    private Set<Organization> organizations = new HashSet<>();

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

    public Tenant name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public Tenant domain(String domain) {
        this.domain = domain;
        return this;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public Tenant subdomain(String subdomain) {
        this.subdomain = subdomain;
        return this;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getLogo() {
        return logo;
    }

    public Tenant logo(String logo) {
        this.logo = logo;
        return this;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public TimeZone getTimezone() {
        return timezone;
    }

    public Tenant timezone(TimeZone timezone) {
        this.timezone = timezone;
        return this;
    }

    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    public String getBookingPolicies() {
        return bookingPolicies;
    }

    public Tenant bookingPolicies(String bookingPolicies) {
        this.bookingPolicies = bookingPolicies;
        return this;
    }

    public void setBookingPolicies(String bookingPolicies) {
        this.bookingPolicies = bookingPolicies;
    }

    public String getHrPolicies() {
        return hrPolicies;
    }

    public Tenant hrPolicies(String hrPolicies) {
        this.hrPolicies = hrPolicies;
        return this;
    }

    public void setHrPolicies(String hrPolicies) {
        this.hrPolicies = hrPolicies;
    }

    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public Tenant subscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
        return this;
    }

    public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }

    public Set<Organization> getOrganizations() {
        return organizations;
    }

    public Tenant organizations(Set<Organization> organizations) {
        this.organizations = organizations;
        return this;
    }

    public void setOrganizations(Set<Organization> organizations) {
        this.organizations = organizations;
    }

    public Tenant addOrganization(Organization organization) {
        this.organizations.add(organization);
        organization.setTenant(this);
        return this;
    }

    public Tenant removeOrganization(Organization organization) {
        this.organizations.remove(organization);
        organization.setTenant(null);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return Objects.equals(id, tenant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Tenant{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", domain='" + domain + '\'' +
            ", subdomain='" + subdomain + '\'' +
            ", timezone=" + (timezone != null ? timezone.getID() : null) +
            '}';
    }
}
