package com.humano.dto.tenant.responses;

import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for tenant onboarding response.
 * Contains the result of a successful tenant onboarding including
 * tenant, subscription, and invoice details.
 */
public record TenantOnboardingResponse(
    // ===== Tenant Information =====
    UUID tenantId,
    String tenantName,
    String subdomain,
    String domain,
    TenantStatus tenantStatus,

    // ===== Subscription Information =====
    UUID subscriptionId,
    SubscriptionStatus subscriptionStatus,
    String subscriptionPlanName,
    Instant subscriptionStartDate,
    Instant currentPeriodEnd,
    Instant trialEndDate,
    boolean isTrialActive,

    // ===== Invoice Information =====
    UUID invoiceId,
    String invoiceNumber,
    BigDecimal invoiceAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    InvoiceStatus invoiceStatus,
    Instant dueDate,

    // ===== Admin User Information =====
    UUID adminUserId,
    String adminEmail,

    // ===== Metadata =====
    Instant onboardedAt,
    String message
) {
    /**
     * Create a success response for trial signup.
     */
    public static TenantOnboardingResponse forTrialSignup(
        UUID tenantId,
        String tenantName,
        String subdomain,
        String domain,
        UUID subscriptionId,
        String planName,
        Instant startDate,
        Instant trialEnd,
        UUID adminUserId,
        String adminEmail
    ) {
        return new TenantOnboardingResponse(
            tenantId,
            tenantName,
            subdomain,
            domain,
            TenantStatus.ACTIVE,
            subscriptionId,
            SubscriptionStatus.TRIAL,
            planName,
            startDate,
            trialEnd,
            trialEnd,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            adminUserId,
            adminEmail,
            Instant.now(),
            "Welcome! Your trial has started. You have access to all features until " + trialEnd
        );
    }

    /**
     * Create a success response for paid signup.
     */
    public static TenantOnboardingResponse forPaidSignup(
        UUID tenantId,
        String tenantName,
        String subdomain,
        String domain,
        UUID subscriptionId,
        String planName,
        Instant startDate,
        Instant periodEnd,
        UUID invoiceId,
        String invoiceNumber,
        BigDecimal amount,
        BigDecimal tax,
        BigDecimal total,
        InvoiceStatus invoiceStatus,
        Instant dueDate,
        UUID adminUserId,
        String adminEmail
    ) {
        return new TenantOnboardingResponse(
            tenantId,
            tenantName,
            subdomain,
            domain,
            TenantStatus.ACTIVE,
            subscriptionId,
            SubscriptionStatus.ACTIVE,
            planName,
            startDate,
            periodEnd,
            null,
            false,
            invoiceId,
            invoiceNumber,
            amount,
            tax,
            total,
            invoiceStatus,
            dueDate,
            adminUserId,
            adminEmail,
            Instant.now(),
            "Welcome! Your subscription is now active."
        );
    }

    /**
     * Create a pending payment response.
     */
    public static TenantOnboardingResponse forPendingPayment(
        UUID tenantId,
        String tenantName,
        String subdomain,
        String domain,
        UUID subscriptionId,
        String planName,
        UUID invoiceId,
        String invoiceNumber,
        BigDecimal amount,
        BigDecimal tax,
        BigDecimal total,
        Instant dueDate,
        UUID adminUserId,
        String adminEmail
    ) {
        return new TenantOnboardingResponse(
            tenantId,
            tenantName,
            subdomain,
            domain,
            TenantStatus.PENDING_SETUP,
            subscriptionId,
            SubscriptionStatus.PENDING_PAYMENT,
            planName,
            null,
            null,
            null,
            false,
            invoiceId,
            invoiceNumber,
            amount,
            tax,
            total,
            InvoiceStatus.PENDING,
            dueDate,
            adminUserId,
            adminEmail,
            Instant.now(),
            "Your account has been created. Please complete payment to activate your subscription."
        );
    }
}
