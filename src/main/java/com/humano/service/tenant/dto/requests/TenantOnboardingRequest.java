package com.humano.service.tenant.dto.requests;

import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.PaymentMethodType;
import jakarta.validation.constraints.*;
import java.util.TimeZone;
import java.util.UUID;

/**
 * DTO for tenant onboarding request.
 * Contains all information needed to sign up a new tenant including
 * company details, subscription selection, and payment information.
 */
public record TenantOnboardingRequest(
    // ===== Company Information =====
    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100, message = "Company name must be between 2 and 100 characters")
    String companyName,

    @NotBlank(message = "Domain is required")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}$", message = "Domain must be a valid domain name")
    String domain,

    @NotBlank(message = "Subdomain is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$",
        message = "Subdomain must contain only letters, numbers, and hyphens"
    )
    @Size(min = 2, max = 63, message = "Subdomain must be between 2 and 63 characters")
    String subdomain,

    @NotNull(message = "Timezone is required") TimeZone timezone,

    // ===== Admin User Information =====
    @NotBlank(message = "Admin first name is required")
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    String adminFirstName,

    @NotBlank(message = "Admin last name is required")
    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    String adminLastName,

    @NotBlank(message = "Admin email is required") @Email(message = "Admin email must be a valid email address") String adminEmail,

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    String adminPassword,

    // ===== Subscription Information =====
    @NotNull(message = "Subscription plan ID is required") UUID subscriptionPlanId,

    @NotNull(message = "Billing cycle is required") BillingCycle billingCycle,

    Boolean startWithTrial,

    Integer trialDays,

    // ===== Payment Information =====
    @NotNull(message = "Payment method is required") PaymentMethodType paymentMethod,

    String paymentToken, // Token from payment provider (e.g., Stripe)

    String cardLast4, // Last 4 digits of card for display

    String cardBrand, // Visa, Mastercard, etc.

    // ===== Billing Address =====
    String billingAddressLine1,

    String billingAddressLine2,

    String billingCity,

    String billingState,

    String billingPostalCode,

    @Size(min = 2, max = 2, message = "Country code must be 2 characters") String billingCountryCode,

    // ===== Optional Coupon =====
    String couponCode
) {
    /**
     * Check if this is a trial signup.
     */
    public boolean isTrialSignup() {
        return Boolean.TRUE.equals(startWithTrial) && trialDays != null && trialDays > 0;
    }
}
