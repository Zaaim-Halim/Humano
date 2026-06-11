package com.humano.service.billing;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the billing lifecycle.
 * Handles:
 * - Subscription renewals
 * - Trial expirations
 * - Overdue invoice handling
 * - Tenant suspension/reactivation
 */
@Service
public class BillingLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(BillingLifecycleService.class);

    private static final int RENEWAL_DAYS_BEFORE = 3; // Generate renewal invoice 3 days before period end
    private static final int TRIAL_EXPIRY_WARNING_DAYS = 3; // Warn 3 days before trial expires
    private static final int GRACE_PERIOD_DAYS = 7; // Days after due date before suspension
    private static final int SUSPENSION_TO_DELETION_DAYS = 30; // Days after suspension before deletion warning

    private static final AtomicLong invoiceCounter = new AtomicLong(System.currentTimeMillis() % 100000);

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final BillingTaxResolver taxResolver;
    private final BillingMailService billingMailService;
    private final TenantAdminEmailResolver adminEmailResolver;
    private final CouponService couponService;
    private final MeterRegistry meterRegistry;

    public BillingLifecycleService(
        SubscriptionRepository subscriptionRepository,
        InvoiceRepository invoiceRepository,
        TenantRepository tenantRepository,
        BillingTaxResolver taxResolver,
        BillingMailService billingMailService,
        TenantAdminEmailResolver adminEmailResolver,
        CouponService couponService,
        MeterRegistry meterRegistry
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.tenantRepository = tenantRepository;
        this.taxResolver = taxResolver;
        this.billingMailService = billingMailService;
        this.adminEmailResolver = adminEmailResolver;
        this.couponService = couponService;
        this.meterRegistry = meterRegistry;
    }

    private void timeTick(String name, Runnable body) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            body.run();
        } finally {
            sample.stop(meterRegistry.timer("scheduled.tick", "name", name));
        }
    }

    // ========== SCHEDULED TASKS ==========

    /**
     * Process subscription renewals daily.
     * Generates invoices for subscriptions nearing their renewal date.
     */
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void processSubscriptionRenewals() {
        timeTick("processSubscriptionRenewals", this::processSubscriptionRenewalsBody);
    }

    private void processSubscriptionRenewalsBody() {
        log.info("Starting subscription renewal processing");

        Instant renewalThreshold = Instant.now().plus(RENEWAL_DAYS_BEFORE, ChronoUnit.DAYS);
        List<Subscription> subscriptionsDue = subscriptionRepository.findSubscriptionsDueForRenewal(
            SubscriptionStatus.ACTIVE,
            renewalThreshold
        );

        int processed = 0;
        int failed = 0;

        for (Subscription subscription : subscriptionsDue) {
            try {
                processRenewal(subscription);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process renewal for subscription: {}", subscription.getId(), e);
                failed++;
            }
        }

        log.info("Subscription renewal processing completed. Processed: {}, Failed: {}", processed, failed);
    }

    /**
     * Process trial expirations daily.
     * Handles trials that are about to expire or have expired.
     */
    @Scheduled(cron = "0 0 3 * * *") // Run at 3 AM daily
    @Transactional
    public void processTrialExpirations() {
        timeTick("processTrialExpirations", this::processTrialExpirationsBody);
    }

    private void processTrialExpirationsBody() {
        log.info("Starting trial expiration processing");

        Instant now = Instant.now();
        List<Subscription> expiringTrials = subscriptionRepository.findExpiringTrials(now);

        int expired = 0;
        int warned = 0;

        for (Subscription subscription : expiringTrials) {
            try {
                if (subscription.getTrialEnd().isBefore(now)) {
                    expireTrial(subscription);
                    expired++;
                } else {
                    // Trial expiring soon - send warning
                    sendTrialExpiryWarning(subscription);
                    warned++;
                }
            } catch (Exception e) {
                log.error("Failed to process trial expiration for subscription: {}", subscription.getId(), e);
            }
        }

        log.info("Trial expiration processing completed. Expired: {}, Warned: {}", expired, warned);
    }

    /**
     * Process overdue invoices daily.
     * Marks invoices as overdue and handles tenant suspension.
     */
    @Scheduled(cron = "0 0 4 * * *") // Run at 4 AM daily
    @Transactional
    public void processOverdueInvoices() {
        timeTick("processOverdueInvoices", this::processOverdueInvoicesBody);
    }

    private void processOverdueInvoicesBody() {
        log.info("Starting overdue invoice processing");

        Instant now = Instant.now();
        List<Invoice> pendingInvoices = invoiceRepository.findByStatus(InvoiceStatus.PENDING);

        int markedOverdue = 0;
        int suspended = 0;

        for (Invoice invoice : pendingInvoices) {
            try {
                if (invoice.getDueDate().isBefore(now)) {
                    // Mark as overdue
                    invoice.setStatus(InvoiceStatus.OVERDUE);
                    invoiceRepository.save(invoice);
                    markedOverdue++;

                    // Check if past grace period - suspend tenant
                    Instant gracePeriodEnd = invoice.getDueDate().plus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
                    if (gracePeriodEnd.isBefore(now)) {
                        suspendTenantForNonPayment(invoice.getTenant(), invoice.getSubscription());
                        suspended++;
                    } else {
                        // Send payment reminder
                        sendPaymentReminder(invoice);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process overdue invoice: {}", invoice.getId(), e);
            }
        }

        log.info("Overdue invoice processing completed. Marked overdue: {}, Suspended: {}", markedOverdue, suspended);
    }

    /**
     * Process pending cancellations daily.
     * Cancels subscriptions that are marked for cancellation at period end.
     */
    @Scheduled(cron = "0 0 5 * * *") // Run at 5 AM daily
    @Transactional
    public void processPendingCancellations() {
        timeTick("processPendingCancellations", this::processPendingCancellationsBody);
    }

    private void processPendingCancellationsBody() {
        log.info("Starting pending cancellation processing");

        Instant now = Instant.now();
        List<Subscription> pendingCancellations = subscriptionRepository.findByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(now);

        int cancelled = 0;

        for (Subscription subscription : pendingCancellations) {
            try {
                cancelSubscription(subscription);
                cancelled++;
            } catch (Exception e) {
                log.error("Failed to process cancellation for subscription: {}", subscription.getId(), e);
            }
        }

        log.info("Pending cancellation processing completed. Cancelled: {}", cancelled);
    }

    // ========== RENEWAL PROCESSING ==========

    /**
     * Process a subscription renewal.
     */
    private void processRenewal(Subscription subscription) {
        log.debug("Processing renewal for subscription: {}", subscription.getId());

        // Check if renewal invoice already exists
        List<Invoice> existingInvoices = invoiceRepository.findBySubscriptionIdAndStatus(subscription.getId(), InvoiceStatus.PENDING);

        if (!existingInvoices.isEmpty()) {
            log.debug("Renewal invoice already exists for subscription: {}", subscription.getId());
            return;
        }

        // Generate renewal invoice
        Invoice invoice = generateRenewalInvoice(subscription);
        log.info("Generated renewal invoice {} for subscription: {}", invoice.getInvoiceNumber(), subscription.getId());

        // P4.3 — Two emails for one event by design: the invoice-issued copy gives the tenant
        // payment-ready details (number, due date, link to pay); the subscription-renewed copy
        // confirms the renewal status. Both go to the same admin email.
        sendInvoiceIssuedNotification(invoice);
        sendRenewalNotification(subscription, invoice);
    }

    /**
     * Generate a renewal invoice for a subscription.
     */
    private Invoice generateRenewalInvoice(Subscription subscription) {
        BigDecimal amount = calculateRenewalAmount(subscription);
        Instant issueDate = Instant.now();

        // P4.5 — re-apply the coupon the tenant signed up with, if still valid.
        // applyToAmount bumps timesRedeemed atomically; if the coupon has expired
        // or hit max redemptions in the interim, the renewal proceeds at full
        // price (we DON'T fail the renewal on a stale coupon — the original
        // sticker price is what's owed). Log + clear the snapshot so a future
        // tick doesn't keep trying.
        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponSnapshot = subscription.getCouponCode();
        String couponCodeOnInvoice = null;
        if (couponSnapshot != null && !couponSnapshot.isBlank()) {
            try {
                CouponService.CouponApplication applied = couponService.applyToAmount(couponSnapshot, amount);
                discountAmount = applied.discountAmount();
                couponCodeOnInvoice = applied.coupon().getCode();
                log.info("Renewal coupon {} applied to subscription {}: -{}", couponSnapshot, subscription.getId(), discountAmount);
            } catch (RuntimeException e) {
                log.warn(
                    "Renewal coupon {} no longer valid for subscription {} ({}); proceeding at full price and clearing snapshot",
                    couponSnapshot,
                    subscription.getId(),
                    e.getMessage()
                );
                subscription.setCouponCode(null);
                subscriptionRepository.save(subscription);
            }
        }
        BigDecimal taxableSubtotal = amount.subtract(discountAmount);

        // P4.1: resolve country VAT/sales-tax via BillingTaxResolver. The lookup uses the
        // tenant's country code at the invoice issue date; both the rate AND the derived
        // amount are persisted on the invoice so a future country-rate change won't
        // retroactively shift this historical record.
        BillingTaxResolver.TaxResult tax = taxResolver.resolve(
            subscription.getTenant().getCountry(),
            subscription.getSubscriptionPlan(),
            taxableSubtotal,
            issueDate.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        );
        BigDecimal totalAmount = taxableSubtotal.add(tax.amount());

        Invoice invoice = new Invoice();
        invoice.setTenant(subscription.getTenant());
        invoice.setSubscription(subscription);
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setAmount(amount);
        invoice.setDiscountAmount(discountAmount.signum() > 0 ? discountAmount : null);
        invoice.setCouponCode(couponCodeOnInvoice);
        invoice.setTaxAmount(tax.amount());
        invoice.setTaxRate(tax.rate());
        invoice.setTotalAmount(totalAmount);
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(subscription.getCurrentPeriodEnd());

        return invoiceRepository.save(invoice);
    }

    /**
     * Calculate renewal amount based on subscription plan and billing cycle.
     */
    private BigDecimal calculateRenewalAmount(Subscription subscription) {
        BigDecimal basePrice = subscription.getSubscriptionPlan().getBasePrice();

        return switch (subscription.getBillingCycle()) {
            case MONTHLY -> basePrice;
            case YEARLY -> basePrice
                .multiply(new BigDecimal("12"))
                .multiply(new BigDecimal("0.85")) // 15% yearly discount
                .setScale(4, RoundingMode.HALF_UP);
        };
    }

    // ========== TRIAL PROCESSING ==========

    /**
     * Expire a trial subscription.
     */
    private void expireTrial(Subscription subscription) {
        log.info("Expiring trial for subscription: {}", subscription.getId());

        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscriptionRepository.save(subscription);

        // Deactivate tenant
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        // Send trial expired notification
        sendTrialExpiredNotification(subscription);
    }

    // ========== SUSPENSION PROCESSING ==========

    /**
     * Suspend a tenant for non-payment.
     */
    private void suspendTenantForNonPayment(Tenant tenant, Subscription subscription) {
        log.info("Suspending tenant {} for non-payment", tenant.getId());

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscriptionRepository.save(subscription);

        // Send suspension notification
        sendSuspensionNotification(tenant, subscription);
    }

    /**
     * Cancel a subscription.
     */
    private void cancelSubscription(Subscription subscription) {
        log.info("Cancelling subscription: {}", subscription.getId());

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setEndDate(Instant.now());
        subscription.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(subscription);

        // Deactivate tenant
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(TenantStatus.DEACTIVATED);
        tenantRepository.save(tenant);

        // Send cancellation confirmation
        sendCancellationConfirmation(subscription);
    }

    // ========== REACTIVATION ==========

    /**
     * Reactivate a suspended tenant after payment.
     */
    @Transactional
    public void reactivateTenant(Tenant tenant) {
        log.info("Reactivating tenant: {}", tenant.getId());

        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        subscriptionRepository
            .findByTenantId(tenant.getId())
            .ifPresent(subscription -> {
                if (subscription.getStatus() == SubscriptionStatus.SUSPENDED || subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscriptionRepository.save(subscription);
                }
            });

        log.info("Tenant {} reactivated successfully", tenant.getId());
    }

    /**
     * Extend subscription period after successful renewal payment.
     */
    @Transactional
    public void extendSubscriptionPeriod(Subscription subscription) {
        log.debug("Extending subscription period for: {}", subscription.getId());

        Instant newPeriodStart = subscription.getCurrentPeriodEnd();
        Instant newPeriodEnd =
            switch (subscription.getBillingCycle()) {
                case MONTHLY -> newPeriodStart.plus(30, ChronoUnit.DAYS);
                case YEARLY -> newPeriodStart.plus(365, ChronoUnit.DAYS);
            };

        subscription.setCurrentPeriodStart(newPeriodStart);
        subscription.setCurrentPeriodEnd(newPeriodEnd);
        subscriptionRepository.save(subscription);

        log.info("Extended subscription {} to: {}", subscription.getId(), newPeriodEnd);
    }

    // ========== HELPER METHODS ==========

    private String generateInvoiceNumber() {
        long counter = invoiceCounter.incrementAndGet();
        return String.format("INV-%d-%05d", Instant.now().getEpochSecond() / 86400, counter % 100000);
    }

    // ========== NOTIFICATION HOOKS (P4.3) ==========
    // Each method resolves the tenant admin email via TenantAdminEmailResolver and
    // dispatches via BillingMailService. MailService.sendEmail is @Async so these
    // calls do not block the surrounding scheduled job or transaction.

    private void sendRenewalNotification(Subscription subscription, Invoice invoice) {
        log.debug("Sending renewal notification for subscription: {}", subscription.getId());
        resolveEmail(subscription.getTenant()).ifPresent(email ->
            billingMailService.sendSubscriptionRenewed(
                email,
                subscription.getTenant().getName(),
                planLabel(subscription),
                subscription.getCurrentPeriodEnd(),
                invoice.getTotalAmount(),
                invoiceCurrency(invoice)
            )
        );
    }

    private void sendTrialExpiryWarning(Subscription subscription) {
        log.debug("Sending trial expiry warning for subscription: {}", subscription.getId());
        Instant trialEnd = subscription.getTrialEnd();
        if (trialEnd == null) return;
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(Instant.now(), trialEnd));
        resolveEmail(subscription.getTenant()).ifPresent(email ->
            billingMailService.sendTrialEnding(email, subscription.getTenant().getName(), trialEnd, daysRemaining)
        );
    }

    private void sendTrialExpiredNotification(Subscription subscription) {
        log.debug("Sending trial expired notification for subscription: {}", subscription.getId());
        // Reuse the trial-ending template with daysRemaining=0; the wording in the
        // template covers both "ends soon" and "ended" cases via the formatted date.
        Instant trialEnd = subscription.getTrialEnd();
        resolveEmail(subscription.getTenant()).ifPresent(email ->
            billingMailService.sendTrialEnding(email, subscription.getTenant().getName(), trialEnd, 0L)
        );
    }

    private void sendPaymentReminder(Invoice invoice) {
        log.debug("Sending payment reminder for invoice: {}", invoice.getId());
        resolveEmail(invoice.getTenant()).ifPresent(email ->
            billingMailService.sendInvoiceIssued(
                email,
                invoice.getTenant().getName(),
                invoice.getInvoiceNumber(),
                invoice.getTotalAmount(),
                invoiceCurrency(invoice),
                invoice.getDueDate()
            )
        );
    }

    private void sendSuspensionNotification(Tenant tenant, Subscription subscription) {
        log.debug("Sending suspension notification for tenant: {}", tenant.getId());
        // No bespoke "suspended" template in v1 — re-use the cancellation shape with
        // an "effective now" date. Distinct templates can land when the user-facing
        // copy diverges; for now, sharing keeps the surface small.
        resolveEmail(tenant).ifPresent(email ->
            billingMailService.sendSubscriptionCancelled(email, tenant.getName(), planLabel(subscription), Instant.now())
        );
    }

    private void sendCancellationConfirmation(Subscription subscription) {
        log.debug("Sending cancellation confirmation for subscription: {}", subscription.getId());
        resolveEmail(subscription.getTenant()).ifPresent(email ->
            billingMailService.sendSubscriptionCancelled(
                email,
                subscription.getTenant().getName(),
                planLabel(subscription),
                subscription.getCurrentPeriodEnd() != null ? subscription.getCurrentPeriodEnd() : Instant.now()
            )
        );
    }

    /** Sends the invoice-issued email triggered by renewal-invoice creation. */
    void sendInvoiceIssuedNotification(Invoice invoice) {
        resolveEmail(invoice.getTenant()).ifPresent(email ->
            billingMailService.sendInvoiceIssued(
                email,
                invoice.getTenant().getName(),
                invoice.getInvoiceNumber(),
                invoice.getTotalAmount(),
                invoiceCurrency(invoice),
                invoice.getDueDate()
            )
        );
    }

    private java.util.Optional<String> resolveEmail(Tenant tenant) {
        if (tenant == null || tenant.getSubdomain() == null) {
            return java.util.Optional.empty();
        }
        java.util.Optional<String> resolved = adminEmailResolver.resolveBillingContact(tenant.getSubdomain());
        if (resolved.isEmpty()) {
            log.warn(
                "No billing contact resolved for tenant '{}' (subdomain '{}') — email skipped",
                tenant.getName(),
                tenant.getSubdomain()
            );
        }
        return resolved;
    }

    private static String planLabel(Subscription subscription) {
        if (subscription == null || subscription.getSubscriptionPlan() == null) {
            return "—";
        }
        var plan = subscription.getSubscriptionPlan();
        return plan.getDisplayName() != null ? plan.getDisplayName() : plan.getSubscriptionType().name();
    }

    private static String invoiceCurrency(Invoice invoice) {
        return invoice.getCurrency() != null ? invoice.getCurrency() : "USD";
    }
}
