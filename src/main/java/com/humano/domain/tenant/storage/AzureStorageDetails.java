package com.humano.domain.tenant.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.humano.domain.enumeration.storage.StorageBackendType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Config for the AZURE Blob Storage backend.
 *
 * @param accountName       storage account name.
 * @param container         blob container name.
 * @param accessKey         access key (encrypt before persistence).
 * @param maxFileSizeBytes  per-file upper bound. Azure block blob limit is 190.7&nbsp;TiB but
 *                          single-PUT is capped at 5000&nbsp;MiB; default to that.
 */
public record AzureStorageDetails(
    @NotBlank String accountName,
    @NotBlank String container,
    @JsonIgnore String accessKey,
    @Positive long maxFileSizeBytes
)
    implements StorageConfigDetails {
    @Override
    public StorageBackendType type() {
        return StorageBackendType.AZURE;
    }
}
