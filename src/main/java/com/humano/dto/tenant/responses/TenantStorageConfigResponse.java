package com.humano.dto.tenant.responses;

import com.humano.domain.enumeration.storage.StorageBackendType;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning tenant storage configuration information.
 * Uses the polymorphic StorageConfigDetails model stored as JSON.
 */
public record TenantStorageConfigResponse(
    UUID id,
    UUID tenantId,
    String tenantName,
    StorageBackendType backend,
    /** Max file size in bytes (null if not applicable). */
    Long maxFileSizeBytes,
    /** Max total storage in bytes (null if not applicable, 0 means unlimited). */
    Long maxTotalSizeBytes,
    boolean active,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
