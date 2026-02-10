package com.humano.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a payment is completed.
 * This can trigger actions like:
 * - Activating tenant/subscription
 * - Sending receipt emails
 * - Updating accounting systems
 */
public record PaymentCompletedEvent(
    UUID paymentId,
    UUID invoiceId,
    String invoiceNumber,
    UUID tenantId,
    String tenantName,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    String externalPaymentId,
    Instant completedAt
) {
    public static PaymentCompletedEvent of(
        UUID paymentId,
        UUID invoiceId,
        String invoiceNumber,
        UUID tenantId,
        String tenantName,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String externalPaymentId
    ) {
        return new PaymentCompletedEvent(
            paymentId,
            invoiceId,
            invoiceNumber,
            tenantId,
            tenantName,
            amount,
            currency,
            paymentMethod,
            externalPaymentId,
            Instant.now()
        );
    }
}
