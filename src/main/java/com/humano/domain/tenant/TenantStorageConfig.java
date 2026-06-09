package com.humano.domain.tenant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.humano.domain.enumeration.storage.StorageBackendType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.tenant.storage.StorageConfigDetails;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Per-tenant storage backend configuration.
 * <p>
 * The actual config payload (paths, credentials, limits) lives on {@link #config}, a sealed
 * polymorphic record serialized as JSON. The {@code backend} column mirrors
 * {@code config.type()} so SQL can filter by backend without parsing JSON.
 * <p>
 * A tenant may have multiple rows but only one with {@link #active}{@code = true}.
 * Enforce that at the service layer (no unique index because uniqueness predicate is conditional).
 */
@Entity
@Table(name = "tenant_storage_config", indexes = { @Index(name = "idx_tenant_storage_config_tenant", columnList = "tenant_id, active") })
public class TenantStorageConfig extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Backend type. Mirrors {@code config.type()} — kept as its own column so admin queries
     * can filter on it without parsing the JSON payload. Set automatically by
     * {@link #setConfig(StorageConfigDetails)}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "backend", nullable = false, length = 16)
    private StorageBackendType backend;

    /**
     * Typed, polymorphic backend config. Holds credentials for cloud backends — exclude this
     * field from any REST response (use a redacted projection in DTOs).
     */
    @NotNull
    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false)
    private StorageConfigDetails config;

    @NotNull
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public StorageBackendType getBackend() {
        return backend;
    }

    public StorageConfigDetails getConfig() {
        return config;
    }

    /** Sets both {@link #config} and the mirrored {@link #backend} column atomically. */
    public void setConfig(StorageConfigDetails config) {
        this.config = config;
        this.backend = config != null ? config.type() : null;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantStorageConfig that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // Deliberately omits `config` — it may carry credentials.
        return (
            "TenantStorageConfig{" +
            "id=" +
            id +
            ", tenant=" +
            (tenant != null ? tenant.getId() : null) +
            ", backend=" +
            backend +
            ", active=" +
            active +
            '}'
        );
    }
}
