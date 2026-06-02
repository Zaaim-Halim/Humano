package com.humano.service.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.domain.tenant.TenantStorageConfig;
import com.humano.repository.tenant.TenantStorageConfigRepository;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Factory service that provides the appropriate FileStorageService implementation
 * based on tenant configuration.
 */
@Service
public class StorageFactory {

    private static final Logger log = LoggerFactory.getLogger(StorageFactory.class);

    private final TenantStorageConfigRepository tenantStorageConfigRepository;
    private final TenantIdResolver tenantIdResolver;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    // Default values from application properties as fallbacks
    private final String defaultStorageType;
    private final String defaultFilesystemRootLocation;

    // Cache of tenant-specific storage services to avoid recreating them for each request
    private final Map<UUID, FileStorageService> storageServiceCache = new HashMap<>();

    public StorageFactory(
        TenantStorageConfigRepository tenantStorageConfigRepository,
        TenantIdResolver tenantIdResolver,
        ObjectMapper objectMapper,
        JdbcTemplate jdbcTemplate,
        @Value("${app.file-storage.type:filesystem}") String defaultStorageType,
        @Value("${app.file-storage.filesystem.root-location:./uploads}") String defaultFilesystemRootLocation
    ) {
        this.tenantStorageConfigRepository = tenantStorageConfigRepository;
        this.tenantIdResolver = tenantIdResolver;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.defaultStorageType = defaultStorageType;
        this.defaultFilesystemRootLocation = defaultFilesystemRootLocation;
    }

    /**
     * Get the storage service for the current tenant.
     *
     * @return the FileStorageService implementation for the current tenant
     * @throws StorageException if the storage service cannot be created
     */
    public FileStorageService getStorageService() {
        // Resolve current subdomain → master-DB tenant UUID
        UUID tenantId;
        try {
            tenantId = tenantIdResolver.requireCurrentTenantId();
        } catch (RuntimeException e) {
            throw new StorageException("No tenant context available for storage operations", e);
        }

        // Check if we already have a cached storage service for this tenant
        if (storageServiceCache.containsKey(tenantId)) {
            return storageServiceCache.get(tenantId);
        }

        // Find the tenant's storage configuration
        Optional<TenantStorageConfig> configOpt = tenantStorageConfigRepository.findByTenant_IdAndActiveTrue(tenantId);

        // Create the appropriate storage service based on the tenant's configuration
        FileStorageService service;
        if (configOpt.isPresent()) {
            TenantStorageConfig config = configOpt.get();
            service = createStorageService(config);
            log.info("Created storage service for tenant {} using provider: {}", tenantId, config.getStorageType());
        } else {
            // Fall back to default configuration
            log.warn("No storage configuration found for tenant {}, using default: {}", tenantId, defaultStorageType);
            service = createDefaultStorageService();
        }

        // Cache the service for future use
        storageServiceCache.put(tenantId, service);
        return service;
    }

    /**
     * Create a storage service based on tenant configuration.
     *
     * @param config the tenant storage configuration
     * @return the appropriate FileStorageService implementation
     * @throws StorageException if the storage service cannot be created
     */
    private FileStorageService createStorageService(TenantStorageConfig config) {
        try {
            switch (config.getStorageType()) {
                case FILESYSTEM:
                    return createFilesystemStorageService(config);
                case DATABASE:
                    return createDatabaseStorageService(config);
                case S3:
                    throw new StorageException("S3 storage not yet implemented");
                case AZURE:
                    throw new StorageException("Azure Blob storage not yet implemented");
                default:
                    throw new StorageException("Unsupported storage type: " + config.getStorageType());
            }
        } catch (Exception e) {
            throw new StorageException("Failed to create storage service for tenant", e);
        }
    }

    /**
     * Create a filesystem storage service based on tenant configuration.
     *
     * @param config the tenant storage configuration
     * @return a FilesystemStorageService configured for the tenant
     */
    private FileStorageService createFilesystemStorageService(TenantStorageConfig config) {
        String rootLocation = config.getStorageLocation();
        if (rootLocation == null || rootLocation.isEmpty()) {
            rootLocation = defaultFilesystemRootLocation + "/" + config.getTenant().getId();
        }

        Path storagePath = Paths.get(rootLocation);
        return new FilesystemStorageService(storagePath);
    }

    /**
     * Create a database storage service based on tenant configuration.
     *
     * @param config the tenant storage configuration
     * @return a DatabaseStorageService configured for the tenant
     */
    private FileStorageService createDatabaseStorageService(TenantStorageConfig config) {
        // Tenant filtering is applied by DatabaseStorageService via TenantIdResolver.
        return new DatabaseStorageService(jdbcTemplate, tenantIdResolver);
    }

    /**
     * Create a default storage service based on application properties.
     *
     * @return the default FileStorageService implementation
     * @throws StorageException if the storage service cannot be created
     */
    private FileStorageService createDefaultStorageService() {
        // Simple default using filesystem storage with tenant subfolder
        UUID tenantId = tenantIdResolver.requireCurrentTenantId();
        String tenantPath = defaultFilesystemRootLocation + "/" + tenantId;
        return new FilesystemStorageService(Paths.get(tenantPath));
    }

    /**
     * Parse a JSON string into a Map.
     *
     * @param json the JSON string to parse
     * @return a Map of configuration values
     * @throws JsonProcessingException if the JSON cannot be parsed
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJson(String json) throws JsonProcessingException {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(json, HashMap.class);
    }
}
