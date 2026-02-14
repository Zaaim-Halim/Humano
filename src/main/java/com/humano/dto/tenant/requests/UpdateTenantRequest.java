package com.humano.dto.tenant.requests;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.TimeZone;

/**
 * DTO record for updating an existing tenant.
 */
public record UpdateTenantRequest(
    @Size(min = 2, max = 100, message = "Tenant name must be between 2 and 100 characters") String name,

    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}$", message = "Domain must be a valid domain name")
    String domain,

    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$",
        message = "Subdomain must contain only letters, numbers, and hyphens"
    )
    @Size(min = 2, max = 63, message = "Subdomain must be between 2 and 63 characters")
    String subdomain,

    String logo,

    TimeZone timezone,

    String bookingPolicies,

    String hrPolicies
) {}
