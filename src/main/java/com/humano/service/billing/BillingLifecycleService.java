package com.humano.service.billing;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.InvoiceLine;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.InvoiceLineKind;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.tenancy.TenantAccessGuard;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private static final List<Integer> TRIAL_REMINDER_DAYS = List.of(7, 3, 1); // "Trial ends soon" emails, days before
    /** After a trial ends without a plan, the workspace stays read-only this long, then it is suspended. */
    public static final int READ_ONLY_DAYS = 14;
    /** An add-on's own monthly invoice is due this many days after it is issued. */
    private static final int ADDON_INVOICE_DUE_DAYS = 14;
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
    private final TenantAccessGuard accessGuard;
    private final SubscriptionAddonRepository addonRepository;

    public BillingLifecycleService(
        SubscriptionRepository subscriptionRepository,
        InvoiceRepository invoiceRepository,
        TenantRepository tenantRepository,
        BillingTaxResolver taxResolver,
        BillingMailService billingMailService,
        TenantAdminEmailResolver adminEmailResolver,
        CouponService couponService,
        MeterRegistry meterRegistry,
        TenantAccessGuard accessGuard,
        SubscriptionAddonRepository addonRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.tenantRepository = tenantRepository;
        this.taxResolver = taxResolver;
        this.billingMailService = billingMailService;
        this.adminEmailResolver = adminEmailResolver;
        this.couponService = couponService;
        this.meterRegistry = meterRegistry;
        this.accessGuard = accessGuard;
        this.addonRepository = addonRepository;
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
        int furthest = TRIAL_REMINDER_DAYS.stream().max(Integer::compare).orElse(0);
        List<Subscription> expiringTrials = subscriptionRepository.findExpiringTrials(now.plus(furthest, ChronoUnit.DAYS));

        int expired = 0;
        int warned = 0;

        for (Subscription subscription : expiringTrials) {
            try {
                if (!subscription.getTrialEnd().isAfter(now)) {
                    if (Boolean.TRUE.equals(subscription.getAutoRenew())) {
                        startPaidPlan(subscription);
                    } else {
                        expireTrial(subscription);
                    }
                    expired++;
                } else if (!Boolean.TRUE.equals(subscription.getAutoRenew()) && reminderDue(subscription.getTrialEnd(), now)) {
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

        // Generate renewal invoice, for the period that starts when the current one ends
        Invoice invoice = generateRenewalInvoice(subscription, subscription.getCurrentPeriodEnd());
        log.info("Generated renewal invoice {} for subscription: {}", invoice.getInvoiceNumber(), subscription.getId());

        // P4.3 — Two emails for one event by design: the invoice-issued copy gives the tenant
        // payment-ready details (number, due date, link to pay); the subscription-renewed copy
        // confirms the renewal status. Both go to the same admin email.
        sendInvoiceIssuedNotification(invoice);
        sendRenewalNotification(subscription, invoice);
    }

    /**
     * Bills the period a subscription has just started (a customer choosing a plan, or choosing
     * one again after its subscription ended): one invoice priced like a renewal, due at the end
     * of that period. Renewals bill the next period ahead of time, so without this the first
     * period of a self-started subscription would never be billed.
     */
    @Transactional
    public Invoice issueFirstInvoice(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository
            .findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
        Instant periodStart = subscription.getCurrentPeriodStart() != null ? subscription.getCurrentPeriodStart() : Instant.now();
        Invoice invoice = generateRenewalInvoice(subscription, periodStart);
        log.info("Issued first invoice {} for subscription: {}", invoice.getInvoiceNumber(), subscription.getId());
        sendInvoiceIssuedNotification(invoice);
        return invoice;
    }

    /**
     * The invoice for one period of the plan starting at {@code periodStart}, due when the current
     * period ends. A monthly plan carries its add-ons on the same invoice, one line each.
     */
    private Invoice generateRenewalInvoice(Subscription subscription, Instant periodStart) {
        Instant periodEnd = periodStart.plus(subscription.getBillingCycle() == BillingCycle.YEARLY ? 365 : 30, ChronoUnit.DAYS);
        List<InvoiceLine> lines = new ArrayList<>();
        BigDecimal planAmount = calculateRenewalAmount(subscription);
        lines.add(new InvoiceLine(InvoiceLineKind.PLAN, planLineDescription(subscription), 1, planAmount, periodStart, periodEnd));
        if (subscription.getBillingCycle() == BillingCycle.MONTHLY) {
            lines.addAll(addonLines(subscription.getId(), periodStart, periodEnd));
        }
        return createInvoice(subscription, lines, subscription.getCurrentPeriodEnd(), null);
    }

    /** "Growth plan, billed monthly". */
    private static String planLineDescription(Subscription subscription) {
        String cycle = subscription.getBillingCycle() == BillingCycle.YEARLY ? "billed yearly (15% off)" : "billed monthly";
        return planLabel(subscription) + " plan, " + cycle;
    }

    /** One line per add-on with packs: e.g. 3 × "Extra storage, 100 GB pack" at its monthly price. */
    private List<InvoiceLine> addonLines(UUID subscriptionId, Instant periodStart, Instant periodEnd) {
        return addonRepository
            .findBySubscriptionId(subscriptionId)
            .stream()
            .filter(a -> a.getQuantity() > 0)
            .map(a -> addonLine(a, periodStart, periodEnd))
            .toList();
    }

    private static InvoiceLine addonLine(SubscriptionAddon addon, Instant periodStart, Instant periodEnd) {
        String description = FeatureCodes.STORAGE.equals(addon.getFeatureCode())
            ? "Extra storage, " + addon.getUnitSize() + " GB pack"
            : addon.getFeatureCode() + " pack of " + addon.getUnitSize();
        return new InvoiceLine(InvoiceLineKind.ADDON, description, addon.getQuantity(), addon.getUnitPrice(), periodStart, periodEnd);
    }

    /**
     * Builds and saves an invoice of {@code lines} (its amount is their sum): the subscription's
     * coupon re-applied to the plan lines only (add-ons are never discounted), country tax resolved
     * at the issue date on what is left, due on {@code dueDate}.
     */
    private Invoice createInvoice(Subscription subscription, List<InvoiceLine> lines, Instant dueDate, String notes) {
        Instant issueDate = Instant.now();
        BigDecimal amount = lines.stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal planAmount = lines
            .stream()
            .filter(l -> l.getKind() == InvoiceLineKind.PLAN)
            .map(InvoiceLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // P4.5 — re-apply the coupon the tenant signed up with, if still valid.
        // applyToAmount bumps timesRedeemed atomically; if the coupon has expired
        // or hit max redemptions in the interim, the renewal proceeds at full
        // price (we DON'T fail the renewal on a stale coupon — the original
        // sticker price is what's owed). Log + clear the snapshot so a future
        // tick doesn't keep trying.
        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponSnapshot = planAmount.signum() > 0 ? subscription.getCouponCode() : null;
        String couponCodeOnInvoice = null;
        if (couponSnapshot != null && !couponSnapshot.isBlank()) {
            try {
                CouponService.CouponApplication applied = couponService.applyToAmount(
                    couponSnapshot,
                    planAmount,
                    subscription.getSubscriptionPlan().getId(),
                    subscription.getBillingCycle()
                );
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
        invoice.setDueDate(dueDate);
        invoice.setNotes(notes);
        lines.forEach(invoice::addLine);

        return invoiceRepository.save(invoice);
    }

    // ========== ADD-ONS (billed monthly, on top of the subscription) ==========

    /**
     * Daily: add-ons of yearly subscriptions get their own invoice each month (a monthly plan
     * carries them on its renewal instead). Each run bills one month per add-on that is due.
     */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void processAddonBilling() {
        timeTick("processAddonBilling", this::processAddonBillingBody);
    }

    private void processAddonBillingBody() {
        Instant now = Instant.now();
        int billed = 0;
        for (SubscriptionAddon addon : addonRepository.findDueForMonthlyInvoice(now)) {
            try {
                Subscription subscription = addon.getSubscription();
                Instant from = addon.getBilledUntil() != null ? addon.getBilledUntil() : now;
                Instant until = from.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
                Invoice invoice = createInvoice(
                    subscription,
                    List.of(addonLine(addon, from, until)),
                    now.plus(ADDON_INVOICE_DUE_DAYS, ChronoUnit.DAYS),
                    null
                );
                addon.setBilledUntil(until);
                addonRepository.save(addon);
                sendInvoiceIssuedNotification(invoice);
                billed++;
            } catch (Exception e) {
                log.error("Failed to bill add-on {}", addon.getId(), e);
            }
        }
        log.info("Add-on billing completed. Invoiced: {}", billed);
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
    /**
     * The trial ended and a plan was chosen during it ({@code autoRenew}): the paid plan starts now
     * and its first invoice is issued.
     */
    private void startPaidPlan(Subscription subscription) {
        log.info("Trial of subscription {} ended with a plan chosen; starting it", subscription.getId());
        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plus(subscription.getBillingCycle() == BillingCycle.YEARLY ? 365 : 30, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
        issueFirstInvoice(subscription.getId());
    }

    /**
     * The trial ended without a plan. The workspace is not suspended yet: while the subscription is
     * {@code EXPIRED} it is read-only ({@link com.humano.tenancy.TenantAccessGuard}), so
     * people can still sign in and an admin can pick a plan; {@link #suspendLapsedTrials()} pauses
     * it {@link #READ_ONLY_DAYS} days later.
     */
    private void expireTrial(Subscription subscription) {
        log.info("Expiring trial for subscription: {}", subscription.getId());

        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setEndDate(now);
        subscriptionRepository.save(subscription);
        accessGuard.evict(subscription.getTenant().getSubdomain());

        Instant readOnlyUntil = now.plus(READ_ONLY_DAYS, ChronoUnit.DAYS);
        resolveEmail(subscription.getTenant()).ifPresent(email ->
            billingMailService.sendTrialEnded(email, subscription.getTenant().getName(), readOnlyUntil)
        );
    }

    /**
     * Whether today's run is the one for a "trial ends soon" reminder: the trial ends within the
     * day that is 7, 3 or 1 days away, so a once-a-day run sends each reminder once.
     */
    private static boolean reminderDue(Instant trialEnd, Instant now) {
        for (int days : TRIAL_REMINDER_DAYS) {
            Instant to = now.plus(days, ChronoUnit.DAYS);
            if (trialEnd.isAfter(to.minus(1, ChronoUnit.DAYS)) && !trialEnd.isAfter(to)) {
                return true;
            }
        }
        return false;
    }

    /** Daily: trials that ended without a plan and whose read-only period is over are suspended (nothing is deleted). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void suspendLapsedTrials() {
        timeTick("suspendLapsedTrials", this::suspendLapsedTrialsBody);
    }

    private void suspendLapsedTrialsBody() {
        Instant cutoff = Instant.now().minus(READ_ONLY_DAYS, ChronoUnit.DAYS);
        int suspended = 0;
        for (Subscription subscription : subscriptionRepository.findByStatus(SubscriptionStatus.EXPIRED)) {
            Tenant tenant = subscription.getTenant();
            if (
                tenant.getStatus() != TenantStatus.ACTIVE || subscription.getEndDate() == null || subscription.getEndDate().isAfter(cutoff)
            ) {
                continue;
            }
            try {
                tenant.setStatus(TenantStatus.SUSPENDED);
                tenantRepository.save(tenant);
                accessGuard.evict(tenant.getSubdomain());
                sendSuspensionNotification(tenant, subscription);
                suspended++;
            } catch (Exception e) {
                log.error("Failed to suspend lapsed trial of tenant: {}", tenant.getId(), e);
            }
        }
        log.info("Lapsed trial processing completed. Suspended: {}", suspended);
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
        // Rounded up: a trial ending in 6 days and 20 hours "ends in 7 days".
        long daysRemaining = Math.max(1, (Duration.between(Instant.now(), trialEnd).toHours() + 23) / 24);
        resolveEmail(subscription.getTenant()).ifPresent(email ->
            billingMailService.sendTrialEnding(email, subscription.getTenant().getName(), trialEnd, daysRemaining)
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
