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
 *
 * <p>Implements {@link TenantScopedEvent}: even though the publisher operates on
 * the master DB, listeners may need to seed tenant-scoped defaults — they read
 * {@code tenantSubdomain()} and switch {@code TenantContext} accordingly.
 */
public record TenantOnboardedEvent(
    UUID tenantId,
    String tenantName,
    String tenantSubdomain,
    String adminEmail,
    UUID subscriptionId,
    String subscriptionPlanName,
    boolean isTrial,
    Instant onboardedAt
)
    implements TenantScopedEvent {
    public static TenantOnboardedEvent of(
        UUID tenantId,
        String tenantName,
        String tenantSubdomain,
        String adminEmail,
        UUID subscriptionId,
        String subscriptionPlanName,
        boolean isTrial
    ) {
        return new TenantOnboardedEvent(
            tenantId,
            tenantName,
            tenantSubdomain,
            adminEmail,
            subscriptionId,
            subscriptionPlanName,
            isTrial,
            Instant.now()
        );
    }
}
