package com.humano.domain.enumeration.tenant;

/**
 * Status of a tenant in the system lifecycle.
 */
public enum TenantStatus {
    PENDING_SETUP, // Tenant created but not fully provisioned
    PROVISIONING, // Tenant is being set up (resources being allocated)
    PROVISIONING_FAILED, // Tenant provisioning failed
    ACTIVE, // Tenant is fully operational
    SUSPENDED, // Tenant suspended due to billing or policy issues
    MIGRATION_FAILED, // Startup Liquibase migration failed (P1.7) — tenant is degraded; needs operator intervention
    DEACTIVATED, // Tenant deactivated by admin
    DELETED, // Tenant marked for deletion
}
