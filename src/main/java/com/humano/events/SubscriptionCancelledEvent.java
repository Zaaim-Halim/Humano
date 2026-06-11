package com.humano.events;

import java.time.Instant;
import java.util.UUID;

/**
 * P4.4 — Published when a subscription transitions to {@code CANCELLED}.
 * <p>
 * Reasons captured by {@link Reason}:
 * <ul>
 *   <li>{@code USER} — explicit cancellation from the SPA.</li>
 *   <li>{@code DUNNING_EXHAUSTED} — DunningService exhausted all retry attempts.</li>
 *   <li>{@code TRIAL_EXPIRED} — trial ended without a paid plan being chosen.</li>
 *   <li>{@code OPERATOR} — platform admin force-cancelled.</li>
 * </ul>
 * Listeners side-effect asynchronously (email via {@code TenantEventListener},
 * future analytics, downstream provisioning teardown).
 *
 * <p>Implements {@link TenantScopedEvent}: provisioning-teardown listeners must
 * route to the tenant DB via {@code tenantSubdomain()} to drop tenant-scoped
 * state.
 */
public record SubscriptionCancelledEvent(
    UUID subscriptionId,
    UUID tenantId,
    String tenantName,
    String tenantSubdomain,
    String planName,
    Reason reason,
    Instant effectiveAt,
    Instant cancelledAt
)
    implements TenantScopedEvent {
    public enum Reason {
        USER,
        DUNNING_EXHAUSTED,
        TRIAL_EXPIRED,
        OPERATOR,
    }

    public static SubscriptionCancelledEvent of(
        UUID subscriptionId,
        UUID tenantId,
        String tenantName,
        String tenantSubdomain,
        String planName,
        Reason reason,
        Instant effectiveAt
    ) {
        return new SubscriptionCancelledEvent(
            subscriptionId,
            tenantId,
            tenantName,
            tenantSubdomain,
            planName,
            reason,
            effectiveAt,
            Instant.now()
        );
    }
}
