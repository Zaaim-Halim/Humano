package com.humano.web.rest.billing;

import com.humano.security.annotation.PublicEndpoint;
import com.humano.service.billing.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P4.2 — Stripe webhook ingress.
 * <p>
 * Stripe POSTs signed event envelopes to this endpoint after asynchronous state
 * changes (3DS authentication, off-session debits, refunds, disputes). We verify
 * the {@code Stripe-Signature} header against {@code humano.billing.stripe.webhook-secret}
 * to confirm the request actually came from Stripe; failed verification is a
 * silent 400 so we don't leak which secret was tried.
 * <p>
 * <b>Filter / security bypass.</b> Stripe does not send our session cookie or
 * X-Tenant-ID header; this path is registered as {@code permitAll} in
 * {@link com.humano.config.SecurityConfiguration} and excluded from
 * {@link com.humano.config.multitenancy.TenantResolutionFilter} via
 * {@code shouldNotFilter}. The webhook locates its target tenant transitively
 * through {@code Payment → Invoice → Tenant}, so no tenant context is needed
 * on the request itself.
 * <p>
 * <b>Idempotency.</b> Stripe re-delivers events on failure; the
 * {@code completeByExternalId / failByExternalId / recordRefundByExternalId}
 * methods on {@link PaymentService} are idempotent on terminal status, so
 * replays are safe.
 */
@RestController
@RequestMapping("/api/billing/webhooks")
public class StripeWebhookResource {

    private static final Logger LOG = LoggerFactory.getLogger(StripeWebhookResource.class);

    private static final BigDecimal MINOR_UNIT_SCALE = new BigDecimal(100);

    private final PaymentService paymentService;
    private final String webhookSecret;

    public StripeWebhookResource(PaymentService paymentService, @Value("${humano.billing.stripe.webhook-secret:}") String webhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PublicEndpoint
    public ResponseEntity<String> handle(
        @RequestHeader(value = "Stripe-Signature", required = false) String signature,
        @RequestBody String payload
    ) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            LOG.warn("Stripe webhook hit but humano.billing.stripe.webhook-secret is empty — rejecting");
            return ResponseEntity.status(503).body("Webhook not configured");
        }
        if (signature == null || signature.isBlank()) {
            LOG.warn("Stripe webhook missing Stripe-Signature header");
            return ResponseEntity.badRequest().body("Missing signature");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            LOG.warn("Stripe webhook signature verification failed");
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            LOG.warn("Stripe webhook payload parse failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        String type = event.getType();
        LOG.info("Stripe webhook received: id={} type={}", event.getId(), type);
        StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
        if (object == null) {
            LOG.warn("Stripe webhook {} has no deserialisable object — acking anyway", event.getId());
            return ResponseEntity.ok("ignored");
        }

        try {
            switch (type) {
                case "payment_intent.succeeded" -> handleIntentSucceeded(event, (PaymentIntent) object);
                case "payment_intent.payment_failed" -> handleIntentFailed(event, (PaymentIntent) object);
                case "charge.refunded" -> handleChargeRefunded(event, (Charge) object);
                default -> LOG.debug("Stripe webhook {} type {} not handled — ack", event.getId(), type);
            }
        } catch (RuntimeException e) {
            // Don't 500 — Stripe would retry. Log + ack so the operator deals with it.
            LOG.error("Stripe webhook {} handler failed: {}", event.getId(), e.getMessage(), e);
            return ResponseEntity.ok("handler-failed-acked");
        }
        return ResponseEntity.ok("ok");
    }

    private void handleIntentSucceeded(Event event, PaymentIntent intent) {
        Map<String, Object> snapshot = snapshot(event, intent.getId(), intent.getStatus(), intent.getAmount(), intent.getCurrency());
        paymentService
            .completeByExternalId(intent.getId(), snapshot)
            .ifPresentOrElse(
                p -> LOG.info("Stripe webhook completed payment {} (intent={})", p.id(), intent.getId()),
                () -> LOG.warn("Stripe webhook payment_intent.succeeded for unknown intent {} — no matching Payment row", intent.getId())
            );
    }

    private void handleIntentFailed(Event event, PaymentIntent intent) {
        String reason = "Stripe declined";
        String code = null;
        if (intent.getLastPaymentError() != null) {
            reason = intent.getLastPaymentError().getMessage() != null ? intent.getLastPaymentError().getMessage() : reason;
            code = intent.getLastPaymentError().getCode();
        }
        Map<String, Object> snapshot = snapshot(event, intent.getId(), intent.getStatus(), intent.getAmount(), intent.getCurrency());
        snapshot.put("failureReason", reason);
        snapshot.put("failureCode", code);
        paymentService
            .failByExternalId(intent.getId(), reason, code, snapshot)
            .ifPresentOrElse(
                p -> LOG.info("Stripe webhook failed payment {} (intent={})", p.id(), intent.getId()),
                () ->
                    LOG.warn("Stripe webhook payment_intent.payment_failed for unknown intent {} — no matching Payment row", intent.getId())
            );
    }

    private void handleChargeRefunded(Event event, Charge charge) {
        // Stripe sends amount_refunded as the CUMULATIVE refunded total. Payment.refundedAmount
        // stores the same model, so the webhook can overwrite without sum-tracking.
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            LOG.warn("Stripe webhook charge.refunded with no payment_intent — ignoring");
            return;
        }
        BigDecimal refundedMajor = new BigDecimal(charge.getAmountRefunded()).divide(MINOR_UNIT_SCALE, 2, RoundingMode.HALF_UP);
        Map<String, Object> snapshot = snapshot(event, paymentIntentId, charge.getStatus(), charge.getAmount(), charge.getCurrency());
        snapshot.put("amountRefunded", charge.getAmountRefunded());
        paymentService
            .recordRefundByExternalId(paymentIntentId, refundedMajor, snapshot)
            .ifPresentOrElse(
                p -> LOG.info("Stripe webhook refund total {} on payment {} (intent={})", refundedMajor, p.id(), paymentIntentId),
                () -> LOG.warn("Stripe webhook charge.refunded for unknown intent {} — no matching Payment row", paymentIntentId)
            );
    }

    private Map<String, Object> snapshot(Event event, String objectId, String status, Long amountMinor, String currency) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("eventId", event.getId());
        snapshot.put("eventType", event.getType());
        snapshot.put("objectId", objectId);
        snapshot.put("status", status);
        snapshot.put("amountMinor", amountMinor);
        snapshot.put("currency", currency);
        return snapshot;
    }
}
