package com.humano.service.storage;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.domain.tenant.TenantStorageConfig;
import com.humano.domain.tenant.storage.FilesystemStorageDetails;
import com.humano.domain.tenant.storage.StorageConfigDetails;
import com.humano.repository.storage.FileBlobRepository;
import com.humano.repository.tenant.TenantStorageConfigRepository;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Returns the {@link FileStorageService} for the current tenant, instantiated from the
 * tenant's active {@link TenantStorageConfig}.
 * <p>
 * Caches one service per tenant id; {@link #invalidate(UUID)} drops the cache entry so the
 * next call picks up a changed config (call from {@code TenantStorageConfigService} on
 * activate/deactivate/delete).
 */
@Service
public class StorageFactory {

    private static final Logger log = LoggerFactory.getLogger(StorageFactory.class);

    private final TenantStorageConfigRepository tenantStorageConfigRepository;
    private final TenantIdResolver tenantIdResolver;
    private final FileBlobRepository fileBlobRepository;
    private final String defaultFilesystemRootLocation;

    private final Map<UUID, FileStorageService> storageServiceCache = new ConcurrentHashMap<>();

    public StorageFactory(
        TenantStorageConfigRepository tenantStorageConfigRepository,
        TenantIdResolver tenantIdResolver,
        FileBlobRepository fileBlobRepository,
        @Value("${app.file-storage.filesystem.root-location:./uploads}") String defaultFilesystemRootLocation
    ) {
        this.tenantStorageConfigRepository = tenantStorageConfigRepository;
        this.tenantIdResolver = tenantIdResolver;
        this.fileBlobRepository = fileBlobRepository;
        this.defaultFilesystemRootLocation = defaultFilesystemRootLocation;
    }

    /**
     * Resolve the active backend for the current tenant. Falls back to filesystem (rooted at
     * {@code app.file-storage.filesystem.root-location/{tenantId}}) when the tenant has no
     * active {@link TenantStorageConfig} yet.
     */
    public FileStorageService getStorageService() {
        UUID tenantId;
        try {
            tenantId = tenantIdResolver.requireCurrentTenantId();
        } catch (RuntimeException e) {
            throw new StorageException("No tenant context available for storage operations", e);
        }
        return storageServiceCache.computeIfAbsent(tenantId, this::build);
    }

    /** Drop the cached service for {@code tenantId}; next {@link #getStorageService()} rebuilds. */
    public void invalidate(UUID tenantId) {
        if (tenantId == null) return;
        storageServiceCache.remove(tenantId);
    }

    private FileStorageService build(UUID tenantId) {
        Optional<TenantStorageConfig> configOpt = tenantStorageConfigRepository.findByTenant_IdAndActiveTrue(tenantId);
        if (configOpt.isEmpty()) {
            log.warn("No active storage configuration for tenant {}, defaulting to filesystem", tenantId);
            return defaultFilesystem(tenantId);
        }
        TenantStorageConfig config = configOpt.get();
        FileStorageService service = createFor(config);
        log.info("Built {} storage backend for tenant {}", config.getBackend(), tenantId);
        return service;
    }

    private FileStorageService createFor(TenantStorageConfig config) {
        StorageConfigDetails details = config.getConfig();
        if (details == null) {
            throw new StorageException("TenantStorageConfig " + config.getId() + " has no backend details");
        }
        return switch (details.type()) {
            case DATABASE -> new DatabaseStorageService(fileBlobRepository);
            case FILESYSTEM -> new FilesystemStorageService(resolveFsRoot((FilesystemStorageDetails) details, config));
            case S3 -> throw new StorageException("S3 storage not yet implemented");
            case AZURE -> throw new StorageException("Azure Blob storage not yet implemented");
        };
    }

    private Path resolveFsRoot(FilesystemStorageDetails fs, TenantStorageConfig config) {
        String root = fs.rootPath();
        if (root == null || root.isBlank()) {
            root = defaultFilesystemRootLocation + "/" + config.getTenant().getId();
        }
        return Paths.get(root);
    }

    private FileStorageService defaultFilesystem(UUID tenantId) {
        return new FilesystemStorageService(Paths.get(defaultFilesystemRootLocation, tenantId.toString()));
    }
}
