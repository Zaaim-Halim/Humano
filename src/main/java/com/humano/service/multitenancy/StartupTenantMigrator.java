package com.humano.service.multitenancy;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.tenant.TenantRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On {@link ApplicationReadyEvent}, run the tenant Liquibase changelog against every
 * {@link TenantStatus#ACTIVE} tenant so adding a new tenant changeset and restarting does
 * not silently leave existing tenants on the old schema (ROADMAP P1.7).
 *
 * <p>Why {@code @EventListener(ApplicationReadyEvent.class)} and not {@code @PostConstruct}:
 * tenants are read from the master DB, which itself only finishes its async Liquibase pass
 * after the context is fully up. {@code ApplicationReadyEvent} is the earliest deterministic
 * point at which both EMFs are usable.
 *
 * <p>Failures are non-fatal: the tenant is flipped to {@link TenantStatus#MIGRATION_FAILED}
 * and the boot proceeds so other tenants stay available.
 */
@Component
public class StartupTenantMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(StartupTenantMigrator.class);
    private static final int DEFAULT_WORKERS = 4;
    private static final int SHUTDOWN_GRACE_MINUTES = 10;

    private final TenantRepository tenantRepository;
    private final TenantMigrationService migrationService;

    public StartupTenantMigrator(TenantRepository tenantRepository, TenantMigrationService migrationService) {
        this.tenantRepository = tenantRepository;
        this.migrationService = migrationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateAllActiveTenants() {
        List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
        if (tenants.isEmpty()) {
            LOG.info("Startup tenant migration: no ACTIVE tenants to migrate");
            return;
        }
        LOG.info("Startup tenant migration: starting for {} ACTIVE tenant(s) with {} workers", tenants.size(), DEFAULT_WORKERS);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(DEFAULT_WORKERS, r -> {
            Thread t = new Thread(r, "tenant-migrator");
            t.setDaemon(true);
            return t;
        });
        try {
            tenants.forEach(tenant -> pool.submit(() -> migrateOne(tenant, ok, failed)));
            pool.shutdown();
            if (!pool.awaitTermination(SHUTDOWN_GRACE_MINUTES, TimeUnit.MINUTES)) {
                LOG.warn("Tenant migrator did not drain within {} min; forcing shutdown", SHUTDOWN_GRACE_MINUTES);
                pool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            if (!pool.isShutdown()) {
                pool.shutdownNow();
            }
        }
        LOG.info("Startup tenant migration: complete — ok={} failed={}", ok.get(), failed.get());
    }

    private void migrateOne(Tenant tenant, AtomicInteger ok, AtomicInteger failed) {
        String subdomain = tenant.getSubdomain();
        try {
            if (tenant.getDatabaseConfig() == null) {
                LOG.warn("Skipping tenant '{}' — no databaseConfig attached", subdomain);
                return;
            }
            migrationService.runMigrations(tenant);
            ok.incrementAndGet();
            LOG.info("Migrated tenant '{}'", subdomain);
        } catch (Exception e) {
            failed.incrementAndGet();
            LOG.error("Migration failed for tenant '{}'; marking MIGRATION_FAILED", subdomain, e);
            try {
                tenant.setStatus(TenantStatus.MIGRATION_FAILED);
                tenantRepository.save(tenant);
            } catch (Exception persistEx) {
                LOG.error("Could not persist MIGRATION_FAILED status for tenant '{}'", subdomain, persistEx);
            }
        }
    }
}
