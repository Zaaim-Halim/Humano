package com.humano.service.tenant;

import com.humano.domain.enumeration.storage.StorageBackendType;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantStorageConfig;
import com.humano.domain.tenant.storage.AzureStorageDetails;
import com.humano.domain.tenant.storage.DatabaseStorageDetails;
import com.humano.domain.tenant.storage.FilesystemStorageDetails;
import com.humano.domain.tenant.storage.S3StorageDetails;
import com.humano.domain.tenant.storage.StorageConfigDetails;
import com.humano.dto.tenant.requests.CreateTenantStorageConfigRequest;
import com.humano.dto.tenant.responses.TenantStorageConfigResponse;
import com.humano.repository.tenant.TenantRepository;
import com.humano.repository.tenant.TenantStorageConfigRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.storage.StorageFactory;
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
    private final StorageFactory storageFactory;

    public TenantStorageConfigService(
        TenantStorageConfigRepository tenantStorageConfigRepository,
        TenantRepository tenantRepository,
        StorageFactory storageFactory
    ) {
        this.tenantStorageConfigRepository = tenantStorageConfigRepository;
        this.tenantRepository = tenantRepository;
        this.storageFactory = storageFactory;
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

        // Build the StorageConfigDetails based on the request's storageType
        StorageConfigDetails configDetails = buildStorageConfigDetails(request);
        config.setConfig(configDetails);

        config.setActive(true);

        TenantStorageConfig savedConfig = tenantStorageConfigRepository.save(config);
        log.info("Created tenant storage config with ID: {}", savedConfig.getId());

        // The newly-active config supersedes any cached backend the factory held for this tenant.
        storageFactory.invalidate(tenant.getId());

        return mapToResponse(savedConfig);
    }

    private StorageConfigDetails buildStorageConfigDetails(CreateTenantStorageConfigRequest request) {
        StorageBackendType backend = request.storageBackend();
        return switch (backend) {
            case DATABASE -> {
                var defaults = DatabaseStorageDetails.defaults();
                long maxFile = request.maxFileSizeMb() != null ? request.maxFileSizeMb() * 1024L * 1024L : defaults.maxFileSizeBytes();
                long maxTotal = request.maxStorageGb() != null
                    ? request.maxStorageGb() * 1024L * 1024L * 1024L
                    : defaults.maxTotalSizeBytes();
                yield new DatabaseStorageDetails(maxFile, maxTotal);
            }
            case FILESYSTEM -> {
                var defaults = FilesystemStorageDetails.defaults();
                long maxBytes = request.maxFileSizeMb() != null ? request.maxFileSizeMb() * 1024L * 1024L : defaults.maxFileSizeBytes();
                String path = request.filesystemPath() != null ? request.filesystemPath() : defaults.rootPath();
                yield new FilesystemStorageDetails(path, maxBytes);
            }
            case S3 -> new S3StorageDetails(
                requireNonBlank(request.s3Bucket(), "s3Bucket"),
                requireNonBlank(request.s3Region(), "s3Region"),
                request.s3AccessKey(),
                request.s3SecretKey(),
                request.maxFileSizeMb() != null ? request.maxFileSizeMb() * 1024L * 1024L : 5L * 1024 * 1024 * 1024
            );
            case AZURE -> new AzureStorageDetails(
                requireNonBlank(request.azureAccountName(), "azureAccountName"),
                requireNonBlank(request.azureContainer(), "azureContainer"),
                request.azureConnectionString(),
                request.maxFileSizeMb() != null ? request.maxFileSizeMb() * 1024L * 1024L : 5000L * 1024 * 1024
            );
        };
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for this storage backend");
        }
        return value;
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
                TenantStorageConfigResponse resp = mapToResponse(tenantStorageConfigRepository.save(config));
                storageFactory.invalidate(config.getTenant().getId());
                return resp;
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
                TenantStorageConfigResponse resp = mapToResponse(tenantStorageConfigRepository.save(config));
                storageFactory.invalidate(config.getTenant().getId());
                return resp;
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

        TenantStorageConfig existing = tenantStorageConfigRepository
            .findById(id)
            .orElseThrow(() -> EntityNotFoundException.create("TenantStorageConfig", id));
        UUID tenantId = existing.getTenant().getId();
        tenantStorageConfigRepository.deleteById(id);
        storageFactory.invalidate(tenantId);
        log.info("Deleted tenant storage config with ID: {}", id);
    }

    private TenantStorageConfigResponse mapToResponse(TenantStorageConfig config) {
        StorageConfigDetails details = config.getConfig();
        Long maxFileSizeBytes = null;
        Long maxTotalSizeBytes = null;
        if (details instanceof DatabaseStorageDetails dbDetails) {
            maxFileSizeBytes = dbDetails.maxFileSizeBytes();
            maxTotalSizeBytes = dbDetails.maxTotalSizeBytes();
        }
        return new TenantStorageConfigResponse(
            config.getId(),
            config.getTenant().getId(),
            config.getTenant().getName(),
            config.getBackend(),
            maxFileSizeBytes,
            maxTotalSizeBytes,
            config.isActive(),
            config.getCreatedBy(),
            config.getCreatedDate(),
            config.getLastModifiedBy(),
            config.getLastModifiedDate()
        );
    }
}
