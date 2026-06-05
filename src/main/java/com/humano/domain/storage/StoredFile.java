package com.humano.domain.storage;

import com.humano.domain.enumeration.storage.FileContext;
import com.humano.domain.enumeration.storage.FileVisibility;
import com.humano.domain.enumeration.storage.StorageBackendType;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Metadata for a file stored by the platform.
 * <p>
 * This row is the canonical reference everywhere outside the storage module. The actual bytes
 * live wherever {@link #backend} points; {@link #storageKey} is the opaque pointer the matching
 * {@code StorageBackend} understands (a {@code file_blob.id} for DATABASE, a relative path for
 * FILESYSTEM, an object key for S3, etc.).
 * <p>
 * Owner is modeled as ({@link #ownerType}, {@link #ownerId}) rather than a hard FK so the same
 * file row can hang off any tenant entity without a join table per owner type. Code that needs
 * a typed owner reference should resolve via the corresponding repository.
 */
@Entity
@Table(
    name = "stored_file",
    indexes = {
        @Index(name = "idx_stored_file_owner", columnList = "owner_type, owner_id"),
        @Index(name = "idx_stored_file_context", columnList = "context"),
        @Index(name = "idx_stored_file_public_token", columnList = "public_token"),
        @Index(name = "idx_stored_file_deleted_at", columnList = "deleted_at"),
    }
)
public class StoredFile extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** What this file is for; drives validation policy. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 50)
    private FileContext context;

    /**
     * Polymorphic owner ref. {@code ownerType} is the lower-cased entity name
     * (e.g. {@code "employee"}, {@code "subscription"}); {@code ownerId} is its UUID.
     * Null means orphan / owned by the tenant itself (e.g. company logo).
     */
    @Size(max = 64)
    @Column(name = "owner_type", length = 64)
    private String ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    /** Original client-supplied filename, kept for download Content-Disposition. */
    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @NotBlank
    @Size(max = 127)
    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Positive
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256 hex of the bytes; useful for de-dup and integrity checks. */
    @Size(max = 64)
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private FileVisibility visibility;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "backend", nullable = false, length = 16)
    private StorageBackendType backend;

    /** Opaque key the matching {@code StorageBackend} resolves to the underlying bytes. */
    @NotBlank
    @Size(max = 512)
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    /**
     * Random token used by the {@code /public/files/{token}} route. Populated only when
     * {@link #visibility} is {@link FileVisibility#PUBLIC}; null otherwise so accidental
     * exposure of a row without PUBLIC visibility still cannot be resolved by token.
     */
    @Size(max = 64)
    @Column(name = "public_token", length = 64, unique = true)
    private String publicToken;

    /** User that uploaded this file. Soft FK — kept across user deletion for audit purposes. */
    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    /**
     * Soft-delete marker. Set when the file is logically removed; the retention worker hard-deletes
     * (and triggers backend cleanup) after the configured grace period. Null means active.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FileContext getContext() {
        return context;
    }

    public void setContext(FileContext context) {
        this.context = context;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public FileVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(FileVisibility visibility) {
        this.visibility = visibility;
    }

    public StorageBackendType getBackend() {
        return backend;
    }

    public void setBackend(StorageBackendType backend) {
        this.backend = backend;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredFile that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "StoredFile{" +
            "id=" +
            id +
            ", context=" +
            context +
            ", owner=" +
            ownerType +
            "/" +
            ownerId +
            ", filename='" +
            originalFilename +
            "', size=" +
            sizeBytes +
            ", visibility=" +
            visibility +
            ", backend=" +
            backend +
            ", deleted=" +
            isDeleted() +
            '}'
        );
    }
}
