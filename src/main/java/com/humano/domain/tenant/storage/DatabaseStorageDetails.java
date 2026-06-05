package com.humano.domain.tenant.storage;

import com.humano.domain.enumeration.storage.StorageBackendType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Config for the DATABASE storage backend (bytes live in {@code file_blob}).
 * <p>
 * Hard cap on {@link #maxFileSizeBytes()} is 16&nbsp;MiB by design: most MySQL deployments default
 * {@code max_allowed_packet} to 64&nbsp;MiB, and we leave headroom for protocol framing /
 * audit fields. If you need to store files larger than this, switch the tenant to
 * {@link FilesystemStorageDetails} or a cloud backend.
 *
 * @param maxFileSizeBytes  per-file upper bound. Enforced by {@code FileService} before the
 *                          insert; the backend reports the same via {@code StorageCapabilities}.
 * @param maxTotalSizeBytes optional tenant-wide quota across all DB-stored files. {@code 0}
 *                          means unlimited; otherwise checked at upload time against the
 *                          current sum in {@code file_blob}.
 */
public record DatabaseStorageDetails(@Positive @Max(16L * 1024 * 1024) long maxFileSizeBytes, @PositiveOrZero long maxTotalSizeBytes)
    implements StorageConfigDetails {
    public DatabaseStorageDetails {
        // Defensive: serializer can pass 0 if absent from JSON
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("DatabaseStorageDetails.maxFileSizeBytes must be positive");
        }
    }

    @Override
    public StorageBackendType type() {
        return StorageBackendType.DATABASE;
    }

    /** Conservative default — used when no explicit config exists yet. */
    public static DatabaseStorageDetails defaults() {
        return new DatabaseStorageDetails(16L * 1024 * 1024, 0L);
    }
}
