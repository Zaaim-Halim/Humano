package com.humano.domain.tenant.storage;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.humano.domain.enumeration.storage.StorageBackendType;

/**
 * Typed, polymorphic configuration for a tenant's storage backend.
 * <p>
 * Persisted as JSON in {@code tenant_storage_config.config} and discriminated by the
 * {@code type} property. Adding a new backend means:
 * <ol>
 *   <li>Add the enum value to {@link StorageBackendType}.</li>
 *   <li>Add a record implementing this interface and list it in {@code @JsonSubTypes} below.</li>
 *   <li>Add the {@code permits} clause member here.</li>
 *   <li>Implement a {@code StorageBackend} for it — the {@code switch} in the factory is
 *       exhaustive because this interface is {@code sealed}, so the compiler will tell you.</li>
 * </ol>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = DatabaseStorageDetails.class, name = "DATABASE"),
        @JsonSubTypes.Type(value = FilesystemStorageDetails.class, name = "FILESYSTEM"),
        @JsonSubTypes.Type(value = S3StorageDetails.class, name = "S3"),
        @JsonSubTypes.Type(value = AzureStorageDetails.class, name = "AZURE"),
    }
)
public sealed interface StorageConfigDetails
    permits DatabaseStorageDetails, FilesystemStorageDetails, S3StorageDetails, AzureStorageDetails {
    /** The discriminator. Mirrors the runtime subtype; serialized as the JSON {@code type} field. */
    StorageBackendType type();

    /**
     * Per-file upper bound the backend will accept. Combined with
     * {@link com.humano.domain.enumeration.storage.FileContext#maxBytes()} by the upload path —
     * the effective cap is {@code min(context.maxBytes(), this.maxFileSizeBytes())}.
     */
    long maxFileSizeBytes();
}
