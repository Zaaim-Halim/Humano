package com.humano.dto.tenant.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for creating a new organization.
 */
public record CreateOrganizationRequest(
    @NotBlank(message = "Organization name is required")
    @Size(min = 2, max = 100, message = "Organization name must be between 2 and 100 characters")
    String name,

    @NotNull(message = "Tenant ID is required") UUID tenantId
) {}
