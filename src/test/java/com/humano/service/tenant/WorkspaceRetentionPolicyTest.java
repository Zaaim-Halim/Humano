package com.humano.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Action;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class WorkspaceRetentionPolicyTest {

    private static final int DAYS = 90;
    private static final int WARN = 7;
    private static final String PLATFORM = "default";
    private static final Instant ENDED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant RETENTION_OVER = ENDED.plus(DAYS, ChronoUnit.DAYS);
    private static final Instant WARN_FROM = RETENTION_OVER.minus(WARN, ChronoUnit.DAYS);

    private static Subscription ended(SubscriptionStatus status, TenantStatus tenantStatus) {
        Tenant tenant = new Tenant();
        tenant.setSubdomain("acme");
        tenant.setStatus(tenantStatus);
        tenant.setDatabaseConfig(new TenantDatabaseConfig());
        Subscription s = new Subscription();
        s.setStatus(status);
        s.setEndDate(ENDED);
        s.setTenant(tenant);
        return s;
    }

    private static Subscription warnedAt(Instant at) {
        Subscription s = ended(SubscriptionStatus.EXPIRED, TenantStatus.SUSPENDED);
        s.getTenant().setDeletionWarningSentAt(at);
        return s;
    }

    private static Action decide(Subscription s, Instant now) {
        return WorkspaceRetentionPolicy.decide(s, now, DAYS, WARN, PLATFORM);
    }

    @Test
    void theAdminIsWarnedOnceTheWarningPeriodStarts() {
        Subscription s = ended(SubscriptionStatus.EXPIRED, TenantStatus.SUSPENDED);
        assertThat(decide(s, WARN_FROM.minusSeconds(1))).isEqualTo(Action.NONE);
        assertThat(decide(s, WARN_FROM)).isEqualTo(Action.WARN);
    }

    @Test
    void withoutAWarningTheWorkspaceIsNeverDeleted() {
        // The warning could not be sent (no recipient, mail server down): it is retried, never skipped.
        Subscription s = ended(SubscriptionStatus.CANCELLED, TenantStatus.DEACTIVATED);
        assertThat(decide(s, RETENTION_OVER)).isEqualTo(Action.WARN);
        assertThat(decide(s, RETENTION_OVER.plus(365, ChronoUnit.DAYS))).isEqualTo(Action.WARN);
    }

    @Test
    void aWarnedWorkspaceIsDeletedWhenTheRetentionPeriodIsOver() {
        Subscription s = warnedAt(WARN_FROM);
        assertThat(decide(s, WARN_FROM.plus(1, ChronoUnit.DAYS))).isEqualTo(Action.NONE);
        assertThat(decide(s, RETENTION_OVER.minusSeconds(1))).isEqualTo(Action.NONE);
        assertThat(decide(s, RETENTION_OVER)).isEqualTo(Action.DELETE);
    }

    @Test
    void aLateWarningStillGetsTheFullNoticePeriod() {
        Instant late = RETENTION_OVER.plus(10, ChronoUnit.DAYS);
        Subscription s = warnedAt(late);
        assertThat(decide(s, late.plus(WARN, ChronoUnit.DAYS).minusSeconds(1))).isEqualTo(Action.NONE);
        assertThat(decide(s, late.plus(WARN, ChronoUnit.DAYS))).isEqualTo(Action.DELETE);
        assertThat(WorkspaceRetentionPolicy.deleteOn(ENDED, late, DAYS, WARN)).isEqualTo(late.plus(WARN, ChronoUnit.DAYS));
        assertThat(WorkspaceRetentionPolicy.deleteOn(ENDED, WARN_FROM, DAYS, WARN)).isEqualTo(RETENTION_OVER);
    }

    @Test
    void aWarningFromAnEarlierLapseDoesNotCount() {
        // Warned, reactivated, then lapsed again: the new lapse needs its own warning.
        Subscription s = warnedAt(ENDED.minus(30, ChronoUnit.DAYS));
        assertThat(decide(s, RETENTION_OVER)).isEqualTo(Action.WARN);
    }

    @Test
    void aCancelledSubscriptionIsHandledLikeALapsedTrial() {
        Subscription s = ended(SubscriptionStatus.CANCELLED, TenantStatus.DEACTIVATED);
        s.getTenant().setDeletionWarningSentAt(WARN_FROM);
        assertThat(decide(s, RETENTION_OVER)).isEqualTo(Action.DELETE);
    }

    @Test
    void liveSubscriptionsAreNeverTouched() {
        for (SubscriptionStatus status : new SubscriptionStatus[] {
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED,
            SubscriptionStatus.PENDING_PAYMENT,
        }) {
            Subscription s = ended(status, TenantStatus.SUSPENDED);
            s.getTenant().setDeletionWarningSentAt(WARN_FROM);
            assertThat(decide(s, RETENTION_OVER)).as(status.name()).isEqualTo(Action.NONE);
        }
    }

    @Test
    void aWorkspaceStillInUseIsNeverTouched() {
        // e.g. reactivated after the subscription row was last updated, or still in its read-only period
        for (TenantStatus status : new TenantStatus[] { TenantStatus.ACTIVE, TenantStatus.MIGRATION_FAILED }) {
            Subscription s = ended(SubscriptionStatus.EXPIRED, status);
            s.getTenant().setDeletionWarningSentAt(WARN_FROM);
            assertThat(decide(s, RETENTION_OVER)).as(status.name()).isEqualTo(Action.NONE);
        }
    }

    @Test
    void anAlreadyDeletedWorkspaceIsLeftAlone() {
        Subscription deleted = ended(SubscriptionStatus.CANCELLED, TenantStatus.DELETED);
        deleted.getTenant().setDeletionWarningSentAt(WARN_FROM);
        assertThat(decide(deleted, RETENTION_OVER)).isEqualTo(Action.NONE);

        Subscription noDatabase = warnedAt(WARN_FROM);
        noDatabase.getTenant().setDatabaseConfig(null);
        assertThat(decide(noDatabase, RETENTION_OVER)).isEqualTo(Action.NONE);
    }

    @Test
    void demosAndThePlatformWorkspaceAreNeverTouched() {
        Subscription demo = warnedAt(WARN_FROM);
        demo.getTenant().setDemo(true);
        assertThat(decide(demo, RETENTION_OVER)).isEqualTo(Action.NONE);

        Subscription platform = warnedAt(WARN_FROM);
        platform.getTenant().setSubdomain("Default");
        assertThat(decide(platform, RETENTION_OVER)).isEqualTo(Action.NONE);
    }

    @Test
    void withoutAnEndDateNothingHappens() {
        Subscription s = warnedAt(WARN_FROM);
        s.setEndDate(null);
        assertThat(decide(s, RETENTION_OVER)).isEqualTo(Action.NONE);
    }
}
