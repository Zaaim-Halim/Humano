package com.humano.service.billing;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Payment;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.PaymentStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.events.SubscriptionCancelledEvent;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P4.4 — Dunning state machine for failed payments.
 * <p>
 * Drives the {@code PAST_DUE → ... → CANCELLED} progression for subscriptions
 * with a failed most-recent payment. Tick cadence is configurable via
 * {@code humano.billing.dunning.cron} (default daily at 06:00).
 * <p>
 * <b>State model.</b> Status stays at {@code PAST_DUE} throughout the dunning
 * lifecycle; the level distinction comes from {@link Subscription#getDunningAttempt()}:
 * <ul>
 *   <li>0 → just transitioned to PAST_DUE; no retry attempted yet.</li>
 *   <li>1 → after first failed retry tick (the "DUNNING_1" phase in the
 *       spec's logical-state vocabulary).</li>
 *   <li>2 → after second failed retry tick (= "DUNNING_2").</li>
 *   <li>≥ max-attempts (default 3) → terminal: subscription moves to CANCELLED
 *       and {@link SubscriptionCancelledEvent} is published with
 *       {@link SubscriptionCancelledEvent.Reason#DUNNING_EXHAUSTED}.</li>
 * </ul>
 * <p>
 * <b>Why not add DUNNING_1/DUNNING_2 to the enum.</b> The counter carries the
 * same information without polluting every other {@code switch (status)} in
 * the codebase + without a status-column migration. The PAST_DUE-with-counter
 * model also makes a successful retry an obvious state reset (back to ACTIVE
 * with counter cleared) without an extra "leaving DUNNING_2" transition.
 * <p>
 * <b>Retry semantics.</b> Each tick looks at the most recent PENDING invoice
 * for the subscription and its most recent FAILED payment. If that payment has
 * a Stripe-shaped {@code externalPaymentId} (prefix {@code pi_}), the tick
 * calls {@link PaymentService#retryPayment(java.util.UUID, String)} re-using
 * that id as the token (Stripe accepts the existing PaymentIntent's
 * payment_method for a follow-up charge). When no token is available (no
 * provider configured, or the original payment never reached Stripe), the
 * dunning counter still advances and the tenant still gets a
 * {@code payment-failed} reminder via the existing
 * {@code PaymentFailedEvent} listener — but the retry is skipped (logged at
 * INFO) rather than synthetically simulating success.
 * <p>
 * <b>Idempotency.</b> The {@code last_dunning_at} column gates same-day
 * re-runs: if a row was already processed today the tick is a no-op for it.
 * Operators triggering a manual run via {@link #runDunningCycle()} get a
 * fresh pass.
 */
@Service
public class DunningService {

    private static final Logger log = LoggerFactory.getLogger(DunningService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;
    private final BillingMailService billingMailService;
    private final TenantAdminEmailResolver adminEmailResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxAttempts;

    public DunningService(
        SubscriptionRepository subscriptionRepository,
        InvoiceRepository invoiceRepository,
        PaymentService paymentService,
        BillingMailService billingMailService,
        TenantAdminEmailResolver adminEmailResolver,
        ApplicationEventPublisher eventPublisher,
        @Value("${humano.billing.dunning.max-attempts:3}") int maxAttempts
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentService = paymentService;
        this.billingMailService = billingMailService;
        this.adminEmailResolver = adminEmailResolver;
        this.eventPublisher = eventPublisher;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Scheduled daily entry. Default cron is 06:00 UTC; tenants in other
     * timezones see the email at their local 06:00 ± offset.
     */
    @Scheduled(cron = "${humano.billing.dunning.cron:0 0 6 * * *}")
    public void runDunningCycle() {
        log.info("Dunning cycle starting (maxAttempts={})", maxAttempts);
        List<Subscription> past = subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE);
        int advanced = 0, retried = 0, cancelled = 0;
        for (Subscription sub : past) {
            try {
                Outcome outcome = processSubscription(sub);
                switch (outcome) {
                    case RETRIED_SUCCESS -> retried++;
                    case ADVANCED -> advanced++;
                    case CANCELLED -> cancelled++;
                    case SKIPPED -> {
                        // idempotent same-day no-op; not a failure.
                    }
                }
            } catch (RuntimeException e) {
                log.error("Dunning failed for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
        log.info(
            "Dunning cycle complete: scanned={} advanced={} retriedSuccess={} cancelled={}",
            past.size(),
            advanced,
            retried,
            cancelled
        );
    }

    @Transactional
    public Outcome processSubscription(Subscription sub) {
        // Idempotency gate: same calendar day, skip.
        if (sub.getLastDunningAt() != null && isSameUtcDay(sub.getLastDunningAt(), Instant.now())) {
            log.debug("Subscription {} already processed today; skipping", sub.getId());
            return Outcome.SKIPPED;
        }

        int currentAttempt = sub.getDunningAttempt() + 1;
        sub.setDunningAttempt(currentAttempt);
        sub.setLastDunningAt(Instant.now());

        // Find the invoice + payment to retry.
        Optional<Payment> lastFailedPayment = findLatestFailedPayment(sub);
        if (lastFailedPayment.isPresent() && lookLikeProviderId(lastFailedPayment.get().getExternalPaymentId())) {
            Payment toRetry = lastFailedPayment.get();
            log.info("Dunning attempt {} for subscription {}: retrying payment {}", currentAttempt, sub.getId(), toRetry.getId());
            try {
                // Re-using externalPaymentId as the next-token: Stripe's PaymentIntent
                // can be re-confirmed with its saved payment_method. PaymentService
                // bumps retryCount, calls provider.charge, publishes
                // PaymentFailedEvent on failure (P4.2 already wires the failure path).
                paymentService.retryPayment(toRetry.getId(), toRetry.getExternalPaymentId());
                // On success PaymentService.retryPayment marks the payment COMPLETED
                // and the invoice + subscription cascade follows via
                // PaymentService.completePayment's existing wiring.
                // Re-load fresh row so we observe the updated status.
                Subscription refreshed = subscriptionRepository.findById(sub.getId()).orElse(sub);
                if (refreshed.getStatus() == SubscriptionStatus.ACTIVE) {
                    log.info("Dunning succeeded for subscription {}: back to ACTIVE", sub.getId());
                    refreshed.setDunningAttempt(0);
                    subscriptionRepository.save(refreshed);
                    return Outcome.RETRIED_SUCCESS;
                }
            } catch (RuntimeException e) {
                log.warn("Dunning retry threw for subscription {}: {}", sub.getId(), e.getMessage());
                // fall through to the cap check; PaymentService already published
                // PaymentFailedEvent on its end so the tenant gets an email.
            }
        } else {
            log.info(
                "Dunning attempt {} for subscription {}: no retryable payment (token absent); counter advanced only",
                currentAttempt,
                sub.getId()
            );
        }

        if (currentAttempt >= maxAttempts) {
            cancelExhausted(sub);
            return Outcome.CANCELLED;
        }

        subscriptionRepository.save(sub);
        return Outcome.ADVANCED;
    }

    private void cancelExhausted(Subscription sub) {
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setEndDate(Instant.now());
        // Reset the counter so a follow-up re-activation starts at 0.
        sub.setDunningAttempt(0);
        subscriptionRepository.save(sub);

        String planName = sub.getSubscriptionPlan() != null && sub.getSubscriptionPlan().getDisplayName() != null
            ? sub.getSubscriptionPlan().getDisplayName()
            : (sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan().getSubscriptionType().name() : "—");
        var tenant = sub.getTenant();
        SubscriptionCancelledEvent event = SubscriptionCancelledEvent.of(
            sub.getId(),
            tenant != null ? tenant.getId() : null,
            tenant != null ? tenant.getName() : "—",
            planName,
            SubscriptionCancelledEvent.Reason.DUNNING_EXHAUSTED,
            Instant.now()
        );
        eventPublisher.publishEvent(event);
        log.info("Dunning exhausted for subscription {}: cancelled", sub.getId());
    }

    private Optional<Payment> findLatestFailedPayment(Subscription sub) {
        List<Invoice> pending = invoiceRepository.findBySubscriptionIdAndStatus(sub.getId(), InvoiceStatus.PENDING);
        return pending
            .stream()
            .flatMap(inv -> inv.getPayments().stream())
            .filter(p -> p.getStatus() == PaymentStatus.FAILED)
            .max(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private static boolean lookLikeProviderId(String externalPaymentId) {
        return externalPaymentId != null && externalPaymentId.startsWith("pi_");
    }

    private static boolean isSameUtcDay(Instant a, Instant b) {
        return a.truncatedTo(ChronoUnit.DAYS).equals(b.truncatedTo(ChronoUnit.DAYS));
    }

    public enum Outcome {
        ADVANCED,
        RETRIED_SUCCESS,
        CANCELLED,
        SKIPPED,
    }
}
