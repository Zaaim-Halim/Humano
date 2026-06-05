package com.humano.dto.tenant.requests;

import com.humano.domain.enumeration.storage.StorageBackendType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a new tenant storage configuration.
 * Uses the polymorphic StorageConfigDetails model stored as JSON.
 */
public record CreateTenantStorageConfigRequest(
    @NotNull(message = "Tenant ID is required") UUID tenantId,

    @NotNull(message = "Storage backend is required") StorageBackendType storageBackend,

    /** Max file size in MB (null = use default). */
    Integer maxFileSizeMb,

    /** Max total storage in GB (null = use default). */
    Integer maxStorageGb,

    /** Base path for FILESYSTEM backend. */
    String filesystemPath,

    /** S3 bucket name for S3 backend. */
    String s3Bucket,

    /** S3 region for S3 backend. */
    String s3Region,

    /** S3 access key for S3 backend. */
    String s3AccessKey,

    /** S3 secret key for S3 backend. */
    String s3SecretKey,

    /** Azure account name for AZURE backend. */
    String azureAccountName,

    /** Azure container name for AZURE backend. */
    String azureContainer,

    /** Azure connection string for AZURE backend. */
    String azureConnectionString
) {}
