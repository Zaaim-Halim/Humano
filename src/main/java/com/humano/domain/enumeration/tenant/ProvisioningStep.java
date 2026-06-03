package com.humano.domain.enumeration.tenant;

/**
 * Highest provisioning step a tenant has reached. Stored as a {@code VARCHAR} on
 * {@code tenant.provisioning_step}; used by {@code TenantProvisioningService} to make
 * provisioning resumable after a JVM crash, with each step idempotent so re-running it
 * is safe (see ROADMAP P1.6).
 *
 * <p>Step order is meaningful — {@link #ordinal()} is the comparison key for "have we
 * already passed this point?".
 */
public enum ProvisioningStep {
    /** Step 1 — master DB row inserted; no DB allocated yet. */
    TENANT_CREATED,
    /** Step 3 — {@code tenant_database_config} row inserted (with encrypted password). */
    CONFIG_CREATED,
    /** Step 4 — physical tenant database + MySQL user created on the target server. */
    DATABASE_CREATED,
    /** Step 5 — Liquibase tenant changelog applied; schema is at head. */
    MIGRATIONS_RUN,
    /** Step 6 — default roles, permissions, admin user, payroll defaults seeded. */
    INITIALIZED,
    /** Step 7 — terminal: {@code TenantStatus = ACTIVE}. */
    COMPLETED,
}
