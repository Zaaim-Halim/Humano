package com.humano.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.dto.platform.compliance.RetentionChangeRequest;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Rules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    // ---- Changing the retention period

    private static final Rules CURRENT = new Rules(90, 7);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Instant NOW = TODAY.atStartOfDay(ZoneOffset.UTC).plusHours(9).toInstant();

    @Test
    void longerOrEqualAppliesNow() {
        assertThat(ComplianceService.effectiveFrom(CURRENT, new RetentionChangeRequest(120, 7, null), TODAY, NOW)).isEqualTo(NOW);
        assertThat(ComplianceService.effectiveFrom(CURRENT, new RetentionChangeRequest(90, 14, TODAY.plusDays(60)), TODAY, NOW)).isEqualTo(
            NOW
        );
    }

    @Test
    void shorterNeedsThirtyDaysNotice() {
        assertThatThrownBy(() -> ComplianceService.effectiveFrom(CURRENT, new RetentionChangeRequest(60, 7, null), TODAY, NOW)
        ).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> ComplianceService.effectiveFrom(CURRENT, new RetentionChangeRequest(90, 3, TODAY.plusDays(29)), TODAY, NOW)
        ).isInstanceOf(BadRequestAlertException.class);
        assertThat(ComplianceService.effectiveFrom(CURRENT, new RetentionChangeRequest(60, 7, TODAY.plusDays(30)), TODAY, NOW)).isEqualTo(
            TODAY.plusDays(30).atStartOfDay(ZoneOffset.UTC).toInstant()
        );
    }

    @Test
    void theWarningMustComeBeforeTheEnd() {
        assertThatThrownBy(() -> ComplianceService.effectiveFrom(CURRENT, new RetentionChangeRequest(30, 30, null), TODAY, NOW)
        ).isInstanceOf(BadRequestAlertException.class);
    }
}
