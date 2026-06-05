package com.humano.domain.enumeration.storage;

/**
 * Backend that physically holds the bytes for a stored file.
 * Decoupled from the metadata model so the same {@code StoredFile} row can be migrated
 * between backends without changing its identity.
 */
public enum StorageBackendType {
    DATABASE,
    FILESYSTEM,
    S3,
    AZURE,
}
