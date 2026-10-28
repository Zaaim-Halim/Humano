package com.humano.service.storage;

import com.humano.domain.enumeration.storage.StorageBackendType;
import com.humano.domain.storage.StoredFile;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.storage.StoredFileRepository;
import com.humano.tenancy.TenantContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erases a workspace's uploaded files from where they are kept outside its database, so that dropping
 * the database leaves nothing behind. Files stored in the database itself ({@code DATABASE} backend) go
 * with it. Must run before the database is dropped: the file references are read from it.
 * <p>
 * All or nothing as far as the caller is concerned: if any file cannot be erased it throws, and the
 * caller keeps the database so the references survive for a retry.
 */
@Service
public class TenantFileEraser {

    private static final Logger log = LoggerFactory.getLogger(TenantFileEraser.class);

    private final StoredFileRepository storedFileRepository;
    private final StorageFactory storageFactory;
    private final TransactionTemplate tenantTx;

    public TenantFileEraser(
        StoredFileRepository storedFileRepository,
        StorageFactory storageFactory,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.storedFileRepository = storedFileRepository;
        this.storageFactory = storageFactory;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    /** Erases every file of {@code tenant} kept outside its database. Throws {@link StorageException} if any is left. */
    public void eraseAll(Tenant tenant) {
        List<Path> roots = storageFactory.filesystemRoots(tenant.getId());
        int erased = 0;
        if (tenant.getDatabaseConfig() != null) {
            for (StoredFile file : storedFiles(tenant.getSubdomain())) {
                if (file.getBackend() == StorageBackendType.DATABASE) {
                    continue;
                }
                if (file.getBackend() != StorageBackendType.FILESYSTEM) {
                    throw new StorageException("Cannot erase file " + file.getId() + " from the " + file.getBackend() + " backend");
                }
                for (Path root : roots) {
                    if (deleteUnder(root, file.getStorageKey())) {
                        erased++;
                    }
                }
            }
        }
        // The default root holds only this tenant's files: anything left there (a file whose row is gone) goes too.
        deleteTree(storageFactory.defaultFilesystemRoot(tenant.getId()));
        storageFactory.invalidate(tenant.getId());
        log.info("Erased {} stored file(s) of workspace '{}'", erased, tenant.getSubdomain());
    }

    /** All the workspace's file rows, soft-deleted ones included (their bytes stay until purged). */
    private List<StoredFile> storedFiles(String subdomain) {
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(subdomain);
        try {
            List<StoredFile> files = tenantTx.execute(tx -> storedFileRepository.findAll());
            return files != null ? files : List.of();
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    /** Deletes {@code key} under {@code root}; a key that would resolve outside the root is refused. */
    private static boolean deleteUnder(Path root, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Path p = Path.of(key);
        Path target = (p.isAbsolute() ? p : root.resolve(p)).normalize();
        if (!target.startsWith(root)) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("Could not erase " + target, e);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        List<IOException> failures = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        failures.add(e);
                    }
                });
        } catch (IOException e) {
            throw new StorageException("Could not erase " + root, e);
        }
        if (!failures.isEmpty()) {
            throw new StorageException("Could not erase " + failures.size() + " path(s) under " + root, failures.get(0));
        }
    }
}
