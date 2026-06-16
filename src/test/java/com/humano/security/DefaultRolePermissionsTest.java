package com.humano.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the single-source-of-truth role→permission mapping. These are the invariants the
 * tenant seeder and the {@code @RequirePermission} gates both rely on: gated permission codes
 * must actually be granted to some role, and the platform catalog must stay isolated from the
 * business catalog.
 */
class DefaultRolePermissionsTest {

    @Test
    void adminHoldsEveryBusinessPermission() {
        Set<String> adminPerms = DefaultRolePermissions.rolePermissions().get(AuthoritiesConstants.ADMIN);
        assertThat(adminPerms).containsAll(DefaultRolePermissions.permissions());
    }

    @Test
    void everyRoleGetsDashboardAndApiAccess() {
        for (Map.Entry<String, Set<String>> entry : DefaultRolePermissions.rolePermissions().entrySet()) {
            assertThat(entry.getValue())
                .as("role %s should have dashboard + API access", entry.getKey())
                .contains(PermissionsConstants.VIEW_DASHBOARD, PermissionsConstants.ACCESS_API);
        }
    }

    @Test
    void payrollAdminCanApprovePayroll() {
        // The live programmatic check in PayrollProcessingService gates on APPROVE_PAYROLL;
        // it must resolve once a tenant is seeded from this mapping.
        assertThat(DefaultRolePermissions.rolePermissions().get(AuthoritiesConstants.PAYROLL_ADMIN)).contains(
            PermissionsConstants.APPROVE_PAYROLL
        );
        assertThat(DefaultRolePermissions.rolePermissions().get(AuthoritiesConstants.ADMIN)).contains(PermissionsConstants.APPROVE_PAYROLL);
    }

    @Test
    void employeeHoldsSelfServiceButNotAdminPermissions() {
        Set<String> employeePerms = DefaultRolePermissions.rolePermissions().get(AuthoritiesConstants.EMPLOYEE);
        assertThat(employeePerms).contains(PermissionsConstants.VIEW_OWN_PAYSLIPS, PermissionsConstants.REQUEST_LEAVE);
        assertThat(employeePerms).doesNotContain(PermissionsConstants.PROCESS_PAYROLL, PermissionsConstants.DELETE_EMPLOYEE);
    }

    @Test
    void platformCatalogIsDisjointFromBusinessCatalog() {
        Set<String> business = DefaultRolePermissions.permissions();
        Set<String> platform = PlatformRolePermissions.permissions();
        assertThat(business).doesNotContainAnyElementsOf(platform);
        assertThat(platform).contains(PermissionsConstants.PROVISION_TENANT, PermissionsConstants.VIEW_PLATFORM_BILLING);
    }

    @Test
    void businessTenantNeverGrantsPlatformRoles() {
        assertThat(DefaultRolePermissions.roles()).doesNotContain(AuthoritiesConstants.PLATFORM_OWNER, AuthoritiesConstants.PLATFORM_ADMIN);
    }

    @Test
    void platformOwnerOutranksPlatformAdmin() {
        Set<String> owner = PlatformRolePermissions.rolePermissions().get(AuthoritiesConstants.PLATFORM_OWNER);
        Set<String> admin = PlatformRolePermissions.rolePermissions().get(AuthoritiesConstants.PLATFORM_ADMIN);
        assertThat(owner).containsAll(admin);
        assertThat(owner).contains(PermissionsConstants.DEPROVISION_TENANT);
        assertThat(admin).doesNotContain(PermissionsConstants.DEPROVISION_TENANT);
    }
}
