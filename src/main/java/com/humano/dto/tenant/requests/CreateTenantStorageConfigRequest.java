package com.humano.dto.tenant.requests;

import com.humano.domain.enumeration.tenant.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a new tenant storage configuration.
 */
public record CreateTenantStorageConfigRequest(
    @NotNull(message = "Tenant ID is required") UUID tenantId,

    @NotNull(message = "Storage type is required") StorageType storageType,

    @NotBlank(message = "Storage location is required") String storageLocation,

    String configJson
) {}
