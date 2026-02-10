package com.humano.service.tenant.dto.requests;

import jakarta.validation.constraints.Size;

/**
 * DTO record for updating an existing organization.
 */
public record UpdateOrganizationRequest(
    @Size(min = 2, max = 100, message = "Organization name must be between 2 and 100 characters") String name
) {}
