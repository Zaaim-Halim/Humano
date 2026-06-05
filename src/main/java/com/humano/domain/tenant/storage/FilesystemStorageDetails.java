package com.humano.domain.tenant.storage;

import com.humano.domain.enumeration.storage.StorageBackendType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Config for the FILESYSTEM storage backend.
 *
 * @param rootPath          absolute or relative root directory the backend writes under.
 *                          Per-tenant subfolders are appended by the backend; do not include
 *                          the tenant id here.
 * @param maxFileSizeBytes  per-file upper bound. Typical default: 1&nbsp;GiB.
 */
public record FilesystemStorageDetails(@NotBlank String rootPath, @Positive long maxFileSizeBytes) implements StorageConfigDetails {
    public FilesystemStorageDetails {
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("FilesystemStorageDetails.rootPath is required");
        }
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("FilesystemStorageDetails.maxFileSizeBytes must be positive");
        }
    }

    @Override
    public StorageBackendType type() {
        return StorageBackendType.FILESYSTEM;
    }

    public static FilesystemStorageDetails defaults() {
        return new FilesystemStorageDetails("./uploads", 1024L * 1024 * 1024);
    }
}
