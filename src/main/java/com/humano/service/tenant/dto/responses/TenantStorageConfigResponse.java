package com.humano.service.tenant.dto.responses;

import com.humano.domain.enumeration.tenant.StorageType;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning tenant storage configuration information.
 */
public record TenantStorageConfigResponse(
    UUID id,
    UUID tenantId,
    String tenantName,
    StorageType storageType,
    String storageLocation,
    boolean active,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
