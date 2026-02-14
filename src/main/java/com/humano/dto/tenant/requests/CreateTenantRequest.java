package com.humano.dto.tenant.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.TimeZone;
import java.util.UUID;

/**
 * DTO record for creating a new tenant.
 */
public record CreateTenantRequest(
    @NotBlank(message = "Tenant name is required")
    @Size(min = 2, max = 100, message = "Tenant name must be between 2 and 100 characters")
    String name,

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

    String logo,

    @NotNull(message = "Timezone is required") TimeZone timezone,

    String bookingPolicies,

    String hrPolicies,

    @NotNull(message = "Subscription plan ID is required") UUID subscriptionPlanId
) {}
