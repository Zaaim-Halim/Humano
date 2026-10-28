package com.humano.service.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import org.junit.jupiter.api.Test;

class ComplianceServiceTest {

    private static final String PLATFORM = "default";

    private static Tenant workspace(TenantStatus status) {
        Tenant t = new Tenant();
        t.setSubdomain("acme");
        t.setStatus(status);
        t.setDatabaseConfig(new TenantDatabaseConfig());
        return t;
    }

    @Test
    void everyCustomerWhoseDataWeHoldIsNotified() {
        for (TenantStatus status : new TenantStatus[] {
            TenantStatus.ACTIVE,
            TenantStatus.SUSPENDED,
            TenantStatus.DEACTIVATED,
            TenantStatus.MIGRATION_FAILED,
        }) {
            assertThat(ComplianceService.receivesNotices(workspace(status), PLATFORM)).as(status.name()).isTrue();
        }
    }

    @Test
    void workspacesWithoutCustomerDataAreNot() {
        for (TenantStatus status : new TenantStatus[] {
            TenantStatus.PENDING_SETUP,
            TenantStatus.PROVISIONING,
            TenantStatus.PROVISIONING_FAILED,
            TenantStatus.DELETED,
        }) {
            assertThat(ComplianceService.receivesNotices(workspace(status), PLATFORM)).as(status.name()).isFalse();
        }
        Tenant dropped = workspace(TenantStatus.DEACTIVATED);
        dropped.setDatabaseConfig(null);
        assertThat(ComplianceService.receivesNotices(dropped, PLATFORM)).isFalse();
    }

    @Test
    void demosAndThePlatformWorkspaceAreNot() {
        Tenant demo = workspace(TenantStatus.ACTIVE);
        demo.setDemo(true);
        assertThat(ComplianceService.receivesNotices(demo, PLATFORM)).isFalse();

        Tenant platform = workspace(TenantStatus.ACTIVE);
        platform.setSubdomain("Default");
        assertThat(ComplianceService.receivesNotices(platform, PLATFORM)).isFalse();
    }
}
