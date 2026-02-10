package com.humano.service.tenant;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Payment;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.PaymentStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Organization;
import com.humano.domain.tenant.Tenant;
import com.humano.events.TenantOnboardedEvent;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.PaymentRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.OrganizationRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.tenant.dto.requests.TenantOnboardingRequest;
import com.humano.service.tenant.dto.responses.TenantOnboardingResponse;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling tenant onboarding.
 * Orchestrates the complete signup flow including:
 * - Tenant creation
 * - Subscription setup
 * - Invoice generation
 * - Payment processing
 * - Tenant provisioning
 */
@Service
public class TenantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(TenantOnboardingService.class);
    private static final String ENTITY_NAME = "tenantOnboarding";
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.00"); // Can be configured per country
    private static final int DEFAULT_INVOICE_DUE_DAYS = 7;
    private static final int DEFAULT_TRIAL_DAYS = 14;

    // Simple invoice number counter - in production, use a database sequence
    private static final AtomicLong invoiceCounter = new AtomicLong(System.currentTimeMillis() % 100000);

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TenantOnboardingService(
        TenantRepository tenantRepository,
        SubscriptionRepository subscriptionRepository,
        SubscriptionPlanRepository subscriptionPlanRepository,
        InvoiceRepository invoiceRepository,
        PaymentRepository paymentRepository,
        OrganizationRepository organizationRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.organizationRepository = organizationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a complete tenant onboarding request.
     * This method orchestrates the entire signup flow.
     *
     * @param request the onboarding request containing all signup information
     * @return the onboarding response with tenant, subscription, and invoice details
     */
    @Transactional
    public TenantOnboardingResponse onboardTenant(TenantOnboardingRequest request) {
        log.info("Starting tenant onboarding for company: {}", request.companyName());

        // Step 1: Validate the request
        validateOnboardingRequest(request);

        // Step 2: Get the subscription plan
        SubscriptionPlan plan = subscriptionPlanRepository
            .findById(request.subscriptionPlanId())
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", request.subscriptionPlanId()));

        if (!plan.isActive()) {
            throw new BadRequestAlertException("Selected subscription plan is not available", ENTITY_NAME, "planinactive");
        }

        // Step 3: Create the tenant
        Tenant tenant = createTenant(request, plan);
        log.info("Created tenant with ID: {}", tenant.getId());

        // Step 4: Create the default organization
        Organization defaultOrg = createDefaultOrganization(tenant);
        log.info("Created default organization with ID: {}", defaultOrg.getId());

        // Step 5: Create the subscription
        Subscription subscription = createSubscription(tenant, plan, request);
        log.info("Created subscription with ID: {}", subscription.getId());

        // Step 6: Handle trial or paid signup
        TenantOnboardingResponse response;
        if (request.isTrialSignup()) {
            response = handleTrialSignup(tenant, subscription, plan, request);
        } else {
            response = handlePaidSignup(tenant, subscription, plan, request);
        }

        // Step 7: Publish onboarding event for async processing
        publishOnboardingEvent(tenant, subscription, request);

        log.info("Completed tenant onboarding for: {} (ID: {})", tenant.getName(), tenant.getId());
        return response;
    }

    /**
     * Validate the onboarding request.
     */
    private void validateOnboardingRequest(TenantOnboardingRequest request) {
        // Check subdomain availability
        if (tenantRepository.existsBySubdomain(request.subdomain())) {
            throw new BadRequestAlertException("Subdomain is already taken", ENTITY_NAME, "subdomaintaken");
        }

        // Check domain availability
        if (tenantRepository.existsByDomain(request.domain())) {
            throw new BadRequestAlertException("Domain is already registered", ENTITY_NAME, "domaintaken");
        }

        // Validate payment info for non-trial signups
        if (!request.isTrialSignup() && (request.paymentToken() == null || request.paymentToken().isBlank())) {
            throw new BadRequestAlertException("Payment information is required for non-trial signups", ENTITY_NAME, "paymentrequired");
        }
    }

    /**
     * Create the tenant entity.
     */
    private Tenant createTenant(TenantOnboardingRequest request, SubscriptionPlan plan) {
        Tenant tenant = new Tenant();
        tenant.setName(request.companyName());
        tenant.setDomain(request.domain());
        tenant.setSubdomain(request.subdomain());
        tenant.setTimezone(request.timezone());
        tenant.setSubscriptionPlan(plan);
        tenant.setStatus(TenantStatus.PENDING_SETUP);

        return tenantRepository.save(tenant);
    }

    /**
     * Create the default organization for the tenant.
     */
    private Organization createDefaultOrganization(Tenant tenant) {
        Organization organization = new Organization();
        organization.setName(tenant.getName() + " - Main");
        organization.setTenant(tenant);

        return organizationRepository.save(organization);
    }

    /**
     * Create the subscription for the tenant.
     */
    private Subscription createSubscription(Tenant tenant, SubscriptionPlan plan, TenantOnboardingRequest request) {
        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setSubscriptionPlan(plan);
        subscription.setBillingCycle(request.billingCycle());
        subscription.setAutoRenew(true);
        subscription.setStartDate(now);
        subscription.setCurrentPeriodStart(now);

        // Calculate period end based on billing cycle
        Instant periodEnd = calculatePeriodEnd(now, request.billingCycle());
        subscription.setCurrentPeriodEnd(periodEnd);

        if (request.isTrialSignup()) {
            int trialDays = request.trialDays() != null ? request.trialDays() : DEFAULT_TRIAL_DAYS;
            subscription.setTrialStart(now);
            subscription.setTrialEnd(now.plus(trialDays, ChronoUnit.DAYS));
            subscription.setStatus(SubscriptionStatus.TRIAL);
        } else {
            subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        }

        return subscriptionRepository.save(subscription);
    }

    /**
     * Handle trial signup flow.
     */
    private TenantOnboardingResponse handleTrialSignup(
        Tenant tenant,
        Subscription subscription,
        SubscriptionPlan plan,
        TenantOnboardingRequest request
    ) {
        // Activate tenant immediately for trial
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        // TODO: Create admin user - this would integrate with UserService
        UUID adminUserId = UUID.randomUUID(); // Placeholder

        return TenantOnboardingResponse.forTrialSignup(
            tenant.getId(),
            tenant.getName(),
            tenant.getSubdomain(),
            tenant.getDomain(),
            subscription.getId(),
            plan.getDisplayName(),
            subscription.getStartDate(),
            subscription.getTrialEnd(),
            adminUserId,
            request.adminEmail()
        );
    }

    /**
     * Handle paid signup flow.
     */
    private TenantOnboardingResponse handlePaidSignup(
        Tenant tenant,
        Subscription subscription,
        SubscriptionPlan plan,
        TenantOnboardingRequest request
    ) {
        // Calculate invoice amounts
        BigDecimal amount = calculateSubscriptionAmount(plan, request.billingCycle());
        BigDecimal taxAmount = calculateTax(amount, request.billingCountryCode());
        BigDecimal totalAmount = amount.add(taxAmount);

        // Create invoice
        Invoice invoice = createInvoice(tenant, subscription, amount, taxAmount, totalAmount);
        log.info("Created invoice with ID: {} (Number: {})", invoice.getId(), invoice.getInvoiceNumber());

        // Process payment
        boolean paymentSuccessful = processPayment(invoice, request, totalAmount);

        if (paymentSuccessful) {
            // Activate tenant and subscription
            activateTenantAndSubscription(tenant, subscription, invoice);

            // TODO: Create admin user
            UUID adminUserId = UUID.randomUUID(); // Placeholder

            return TenantOnboardingResponse.forPaidSignup(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getDomain(),
                subscription.getId(),
                plan.getDisplayName(),
                subscription.getStartDate(),
                subscription.getCurrentPeriodEnd(),
                invoice.getId(),
                invoice.getInvoiceNumber(),
                amount,
                taxAmount,
                totalAmount,
                InvoiceStatus.PAID,
                invoice.getDueDate(),
                adminUserId,
                request.adminEmail()
            );
        } else {
            // Return pending payment response
            UUID adminUserId = UUID.randomUUID(); // Placeholder

            return TenantOnboardingResponse.forPendingPayment(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getDomain(),
                subscription.getId(),
                plan.getDisplayName(),
                invoice.getId(),
                invoice.getInvoiceNumber(),
                amount,
                taxAmount,
                totalAmount,
                invoice.getDueDate(),
                adminUserId,
                request.adminEmail()
            );
        }
    }

    /**
     * Create an invoice for the subscription.
     */
    private Invoice createInvoice(
        Tenant tenant,
        Subscription subscription,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
    ) {
        Invoice invoice = new Invoice();
        invoice.setTenant(tenant);
        invoice.setSubscription(subscription);
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setAmount(amount);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setIssueDate(Instant.now());
        invoice.setDueDate(Instant.now().plus(DEFAULT_INVOICE_DUE_DAYS, ChronoUnit.DAYS));

        return invoiceRepository.save(invoice);
    }

    /**
     * Process payment for the invoice.
     * In production, this would integrate with payment providers like Stripe.
     */
    private boolean processPayment(Invoice invoice, TenantOnboardingRequest request, BigDecimal amount) {
        try {
            // TODO: Integrate with actual payment provider (Stripe, etc.)
            // For now, simulate successful payment if payment token is provided

            if (request.paymentToken() == null || request.paymentToken().isBlank()) {
                return false;
            }

            // Create payment record
            Payment payment = new Payment();
            payment.setInvoice(invoice);
            payment.setAmount(amount);
            payment.setMethodType(request.paymentMethod());
            payment.setExternalPaymentId("sim_" + UUID.randomUUID().toString().substring(0, 8));
            payment.setPaymentDate(Instant.now());
            payment.setStatus(PaymentStatus.COMPLETED);

            paymentRepository.save(payment);
            log.info("Payment processed successfully for invoice: {}", invoice.getInvoiceNumber());

            return true;
        } catch (Exception e) {
            log.error("Payment processing failed for invoice: {}", invoice.getInvoiceNumber(), e);
            return false;
        }
    }

    /**
     * Activate tenant and subscription after successful payment.
     */
    private void activateTenantAndSubscription(Tenant tenant, Subscription subscription, Invoice invoice) {
        // Update tenant status
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        // Update subscription status
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);

        // Update invoice status
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidDate(Instant.now());
        invoiceRepository.save(invoice);

        log.info("Activated tenant: {} and subscription: {}", tenant.getId(), subscription.getId());
    }

    /**
     * Calculate subscription amount based on plan and billing cycle.
     */
    private BigDecimal calculateSubscriptionAmount(SubscriptionPlan plan, BillingCycle billingCycle) {
        BigDecimal basePrice = plan.getBasePrice();

        return switch (billingCycle) {
            case MONTHLY -> basePrice;
            case YEARLY -> basePrice.multiply(new BigDecimal("12")).multiply(new BigDecimal("0.85")); // 15% discount for yearly
        };
    }

    /**
     * Calculate tax amount based on country.
     */
    private BigDecimal calculateTax(BigDecimal amount, String countryCode) {
        // TODO: Implement proper tax calculation based on country/region
        // For now, return zero tax
        return amount.multiply(DEFAULT_TAX_RATE).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate period end based on billing cycle.
     */
    private Instant calculatePeriodEnd(Instant startDate, BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> startDate.plus(30, ChronoUnit.DAYS);
            case YEARLY -> startDate.plus(365, ChronoUnit.DAYS);
        };
    }

    /**
     * Generate a unique invoice number.
     */
    private String generateInvoiceNumber() {
        long counter = invoiceCounter.incrementAndGet();
        return String.format("INV-%d-%05d", Instant.now().getEpochSecond() / 86400, counter % 100000);
    }

    /**
     * Publish tenant onboarding event for async processing.
     */
    private void publishOnboardingEvent(Tenant tenant, Subscription subscription, TenantOnboardingRequest request) {
        TenantOnboardedEvent event = TenantOnboardedEvent.of(
            tenant.getId(),
            tenant.getName(),
            tenant.getSubdomain(),
            request.adminEmail(),
            subscription.getId(),
            subscription.getSubscriptionPlan().getDisplayName(),
            subscription.getStatus() == SubscriptionStatus.TRIAL
        );
        eventPublisher.publishEvent(event);
        log.debug("Published onboarding event for tenant: {}", tenant.getId());
    }

    /**
     * Complete payment for a pending subscription.
     * Called when a customer completes payment after initial signup.
     */
    @Transactional
    public TenantOnboardingResponse completePayment(UUID tenantId, String paymentToken) {
        log.info("Completing payment for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> EntityNotFoundException.create("Tenant", tenantId));

        if (tenant.getStatus() != TenantStatus.PENDING_SETUP) {
            throw new BadRequestAlertException("Tenant is not in pending status", ENTITY_NAME, "invalidstatus");
        }

        Subscription subscription = subscriptionRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> EntityNotFoundException.create("Subscription for tenant", tenantId));

        Invoice invoice = invoiceRepository
            .findBySubscriptionIdAndStatus(subscription.getId(), InvoiceStatus.PENDING)
            .stream()
            .findFirst()
            .orElseThrow(() -> EntityNotFoundException.create("Pending invoice for subscription", subscription.getId()));

        // Process payment
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getTotalAmount());
        payment.setExternalPaymentId("pay_" + UUID.randomUUID().toString().substring(0, 8));
        payment.setPaymentDate(Instant.now());
        payment.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        // Activate tenant and subscription
        activateTenantAndSubscription(tenant, subscription, invoice);

        SubscriptionPlan plan = subscription.getSubscriptionPlan();

        return TenantOnboardingResponse.forPaidSignup(
            tenant.getId(),
            tenant.getName(),
            tenant.getSubdomain(),
            tenant.getDomain(),
            subscription.getId(),
            plan.getDisplayName(),
            subscription.getStartDate(),
            subscription.getCurrentPeriodEnd(),
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getAmount(),
            invoice.getTaxAmount(),
            invoice.getTotalAmount(),
            InvoiceStatus.PAID,
            invoice.getDueDate(),
            null, // Admin user ID would come from UserService
            null // Admin email would come from UserService
        );
    }

    /**
     * Convert a trial subscription to a paid subscription.
     */
    @Transactional
    public TenantOnboardingResponse convertTrialToPaid(UUID tenantId, String paymentToken) {
        log.info("Converting trial to paid for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> EntityNotFoundException.create("Tenant", tenantId));

        Subscription subscription = subscriptionRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> EntityNotFoundException.create("Subscription for tenant", tenantId));

        if (subscription.getStatus() != SubscriptionStatus.TRIAL) {
            throw new BadRequestAlertException("Subscription is not in trial status", ENTITY_NAME, "notintrial");
        }

        SubscriptionPlan plan = subscription.getSubscriptionPlan();
        Instant now = Instant.now();

        // Calculate amounts
        BigDecimal amount = calculateSubscriptionAmount(plan, subscription.getBillingCycle());
        BigDecimal taxAmount = calculateTax(amount, null);
        BigDecimal totalAmount = amount.add(taxAmount);

        // Create invoice
        Invoice invoice = createInvoice(tenant, subscription, amount, taxAmount, totalAmount);

        // Process payment
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(totalAmount);
        payment.setExternalPaymentId("pay_" + UUID.randomUUID().toString().substring(0, 8));
        payment.setPaymentDate(now);
        payment.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        // Update subscription
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setTrialEnd(now);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(calculatePeriodEnd(now, subscription.getBillingCycle()));
        subscriptionRepository.save(subscription);

        // Update invoice
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidDate(now);
        invoiceRepository.save(invoice);

        log.info("Converted trial to paid for tenant: {}", tenantId);

        return TenantOnboardingResponse.forPaidSignup(
            tenant.getId(),
            tenant.getName(),
            tenant.getSubdomain(),
            tenant.getDomain(),
            subscription.getId(),
            plan.getDisplayName(),
            subscription.getStartDate(),
            subscription.getCurrentPeriodEnd(),
            invoice.getId(),
            invoice.getInvoiceNumber(),
            amount,
            taxAmount,
            totalAmount,
            InvoiceStatus.PAID,
            invoice.getDueDate(),
            null,
            null
        );
    }
}
