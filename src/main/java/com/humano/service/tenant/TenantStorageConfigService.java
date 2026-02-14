package com.humano.service.tenant;

import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantStorageConfig;
import com.humano.dto.tenant.requests.CreateTenantStorageConfigRequest;
import com.humano.dto.tenant.responses.TenantStorageConfigResponse;
import com.humano.repository.tenant.TenantRepository;
import com.humano.repository.tenant.TenantStorageConfigRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing tenant storage configurations.
 * Handles CRUD operations for tenant storage config entities.
 */
@Service
public class TenantStorageConfigService {

    private static final Logger log = LoggerFactory.getLogger(TenantStorageConfigService.class);

    private final TenantStorageConfigRepository tenantStorageConfigRepository;
    private final TenantRepository tenantRepository;

    public TenantStorageConfigService(TenantStorageConfigRepository tenantStorageConfigRepository, TenantRepository tenantRepository) {
        this.tenantStorageConfigRepository = tenantStorageConfigRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Create a new tenant storage configuration.
     *
     * @param request the storage config creation request
     * @return the created storage config response
     */
    @Transactional
    public TenantStorageConfigResponse createStorageConfig(CreateTenantStorageConfigRequest request) {
        log.debug("Request to create TenantStorageConfig: {}", request);

        Tenant tenant = tenantRepository
            .findById(request.tenantId())
            .orElseThrow(() -> EntityNotFoundException.create("Tenant", request.tenantId()));

        TenantStorageConfig config = new TenantStorageConfig();
        config.setTenant(tenant);
        config.setStorageType(request.storageType());
        config.setStorageLocation(request.storageLocation());
        config.setConfigJson(request.configJson());
        config.setActive(true);

        TenantStorageConfig savedConfig = tenantStorageConfigRepository.save(config);
        log.info("Created tenant storage config with ID: {}", savedConfig.getId());

        return mapToResponse(savedConfig);
    }

    /**
     * Get a storage configuration by ID.
     *
     * @param id the ID of the storage config
     * @return the storage config response
     */
    @Transactional(readOnly = true)
    public TenantStorageConfigResponse getStorageConfigById(UUID id) {
        log.debug("Request to get TenantStorageConfig by ID: {}", id);

        return tenantStorageConfigRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("TenantStorageConfig", id));
    }

    /**
     * Get active storage configuration for a tenant.
     *
     * @param tenantId the tenant ID
     * @return the active storage config response
     */
    @Transactional(readOnly = true)
    public TenantStorageConfigResponse getActiveStorageConfigByTenant(UUID tenantId) {
        log.debug("Request to get active TenantStorageConfig by Tenant: {}", tenantId);

        return tenantStorageConfigRepository
            .findByTenant_IdAndActiveTrue(tenantId)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Active TenantStorageConfig for Tenant", tenantId));
    }

    /**
     * Get all storage configurations with pagination.
     *
     * @param pageable pagination information
     * @return page of storage config responses
     */
    @Transactional(readOnly = true)
    public Page<TenantStorageConfigResponse> getAllStorageConfigs(Pageable pageable) {
        log.debug("Request to get all TenantStorageConfigs");

        return tenantStorageConfigRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Activate a storage configuration.
     *
     * @param id the ID of the storage config to activate
     * @return the updated storage config response
     */
    @Transactional
    public TenantStorageConfigResponse activateStorageConfig(UUID id) {
        log.debug("Request to activate TenantStorageConfig: {}", id);

        return tenantStorageConfigRepository
            .findById(id)
            .map(config -> {
                // Deactivate any existing active config for this tenant
                tenantStorageConfigRepository
                    .findByTenant_IdAndActiveTrue(config.getTenant().getId())
                    .ifPresent(activeConfig -> {
                        activeConfig.setActive(false);
                        tenantStorageConfigRepository.save(activeConfig);
                    });

                config.setActive(true);
                return mapToResponse(tenantStorageConfigRepository.save(config));
            })
            .orElseThrow(() -> EntityNotFoundException.create("TenantStorageConfig", id));
    }

    /**
     * Deactivate a storage configuration.
     *
     * @param id the ID of the storage config to deactivate
     * @return the updated storage config response
     */
    @Transactional
    public TenantStorageConfigResponse deactivateStorageConfig(UUID id) {
        log.debug("Request to deactivate TenantStorageConfig: {}", id);

        return tenantStorageConfigRepository
            .findById(id)
            .map(config -> {
                config.setActive(false);
                return mapToResponse(tenantStorageConfigRepository.save(config));
            })
            .orElseThrow(() -> EntityNotFoundException.create("TenantStorageConfig", id));
    }

    /**
     * Delete a storage configuration by ID.
     *
     * @param id the ID of the storage config to delete
     */
    @Transactional
    public void deleteStorageConfig(UUID id) {
        log.debug("Request to delete TenantStorageConfig: {}", id);

        if (!tenantStorageConfigRepository.existsById(id)) {
            throw EntityNotFoundException.create("TenantStorageConfig", id);
        }
        tenantStorageConfigRepository.deleteById(id);
        log.info("Deleted tenant storage config with ID: {}", id);
    }

    private TenantStorageConfigResponse mapToResponse(TenantStorageConfig config) {
        return new TenantStorageConfigResponse(
            config.getId(),
            config.getTenant().getId(),
            config.getTenant().getName(),
            config.getStorageType(),
            config.getStorageLocation(),
            config.isActive(),
            config.getCreatedBy(),
            config.getCreatedDate(),
            config.getLastModifiedBy(),
            config.getLastModifiedDate()
        );
    }
}
