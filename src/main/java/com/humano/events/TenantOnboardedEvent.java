package com.humano.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a new tenant is successfully onboarded.
 * This event can be consumed by listeners to perform async tasks like:
 * - Sending welcome emails
 * - Setting up default data
 * - Provisioning tenant-specific resources
 * - Notifying administrators
 */
public record TenantOnboardedEvent(
    UUID tenantId,
    String tenantName,
    String subdomain,
    String adminEmail,
    UUID subscriptionId,
    String subscriptionPlanName,
    boolean isTrial,
    Instant onboardedAt
) {
    public static TenantOnboardedEvent of(
        UUID tenantId,
        String tenantName,
        String subdomain,
        String adminEmail,
        UUID subscriptionId,
        String subscriptionPlanName,
        boolean isTrial
    ) {
        return new TenantOnboardedEvent(
            tenantId,
            tenantName,
            subdomain,
            adminEmail,
            subscriptionId,
            subscriptionPlanName,
            isTrial,
            Instant.now()
        );
    }
}
