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
 */
public record SubscriptionCancelledEvent(
    UUID subscriptionId,
    UUID tenantId,
    String tenantName,
    String planName,
    Reason reason,
    Instant effectiveAt,
    Instant cancelledAt
) {
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
        String planName,
        Reason reason,
        Instant effectiveAt
    ) {
        return new SubscriptionCancelledEvent(subscriptionId, tenantId, tenantName, planName, reason, effectiveAt, Instant.now());
    }
}
