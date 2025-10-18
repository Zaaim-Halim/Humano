package com.humano.domain.tenant;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.tenant.StorageType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.Objects;
import java.util.UUID;

/**
 * Entity for storing tenant-specific storage configuration.
 * Each tenant can have its own storage provider type and configuration.
 */
@Entity
@Table(name = "tenant_storage_config")
public class TenantStorageConfig extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private StorageType storageType;

    /**
     * Base path or container name specific to this tenant
     */
    @Column(name = "storage_location", nullable = false)
    private String storageLocation;

    /**
     * JSON string containing provider-specific configuration
     */
    @Column(name = "config_json", length = 2000)
    private String configJson;

    /**
     * Whether this configuration is active for the tenant
     */
    @Column(name = "active")
    private boolean active = true;

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

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantStorageConfig that = (TenantStorageConfig) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "TenantStorageConfig{" +
            "id=" + id +
            ", tenant=" + (tenant != null ? tenant.getId() : null) +
            ", storageType=" + storageType +
            ", storageLocation='" + storageLocation + '\'' +
            ", active=" + active +
            '}';
    }
}
