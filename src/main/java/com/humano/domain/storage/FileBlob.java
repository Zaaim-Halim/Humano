package com.humano.domain.storage;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Raw bytes for files stored by the DATABASE backend.
 * <p>
 * Kept deliberately minimal — one column for content, one for length, nothing else. All
 * metadata lives on {@link StoredFile}; the {@code id} here equals the {@code storage_key} on
 * the matching {@code StoredFile} row.
 * <p>
 * The {@code content} column is {@link FetchType#LAZY} so listing/admin queries that don't
 * need bytes never pay the cost of loading them.
 */
@Entity
@Table(name = "file_blob")
public class FileBlob {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    /** Redundant with {@link StoredFile#getSizeBytes()} but lets us avoid loading the LOB to size-check. */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileBlob that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
