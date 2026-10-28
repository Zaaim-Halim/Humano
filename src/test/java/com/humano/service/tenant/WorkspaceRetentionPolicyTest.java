package com.humano.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Action;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Rules;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    // ---- Periods changed in the console

    private static Version version(Instant from, int days, int warningDays) {
        return new Version(from, days, warningDays, "owner", from);
    }

    private static final Version DEFAULT = new Version(Instant.EPOCH, DAYS, WARN, null, null);

    @Test
    void aLongerPeriodAppliesToEveryoneAtOnce() {
        Instant changed = ENDED.plus(10, ChronoUnit.DAYS);
        List<Version> versions = List.of(DEFAULT, version(changed, 120, 14));
        assertThat(WorkspaceRetentionPolicy.rulesFor(versions, ENDED, changed)).isEqualTo(new Rules(120, 14));
    }

    @Test
    void aShorterPeriodNeverReachesBackToSubscriptionsThatEndedBefore() {
        Instant takesEffect = ENDED.plus(30, ChronoUnit.DAYS);
        List<Version> versions = List.of(DEFAULT, version(takesEffect, 60, 3));
        // Ended under the 90-day promise: keeps it, even once the shorter period is in force.
        assertThat(WorkspaceRetentionPolicy.rulesFor(versions, ENDED, takesEffect.plus(1, ChronoUnit.DAYS))).isEqualTo(
            new Rules(DAYS, WARN)
        );
        // Ending after it took effect: the shorter period.
        Instant laterEnd = takesEffect.plus(5, ChronoUnit.DAYS);
        assertThat(WorkspaceRetentionPolicy.rulesFor(versions, laterEnd, laterEnd)).isEqualTo(new Rules(60, 3));
    }

    @Test
    void aScheduledChangeIsNotInForceBeforeItsDate() {
        Instant takesEffect = ENDED.plus(30, ChronoUnit.DAYS);
        List<Version> versions = List.of(DEFAULT, version(takesEffect, 60, 3));
        assertThat(WorkspaceRetentionPolicy.inForce(versions, takesEffect.minusSeconds(1))).isEqualTo(DEFAULT);
        assertThat(WorkspaceRetentionPolicy.inForce(versions, takesEffect).days()).isEqualTo(60);
    }

    @Test
    void neverDeletedBeforeTheDateTheWarningGave() {
        // Warned for deletion on RETENTION_OVER; the period has since been shortened to 60 days.
        Subscription s = warnedAt(WARN_FROM);
        s.getTenant().setDeletionScheduledFor(RETENTION_OVER);
        Instant underShorterRules = ENDED.plus(60, ChronoUnit.DAYS).plus(WARN + 1, ChronoUnit.DAYS);
        assertThat(WorkspaceRetentionPolicy.decide(s, underShorterRules, 60, WARN, PLATFORM)).isEqualTo(Action.NONE);
        assertThat(WorkspaceRetentionPolicy.decide(s, RETENTION_OVER, 60, WARN, PLATFORM)).isEqualTo(Action.DELETE);
    }

    @Test
    void anAnnouncementFromAnEarlierLapseDoesNotCount() {
        Subscription s = warnedAt(ENDED.minus(200, ChronoUnit.DAYS));
        s.getTenant().setDeletionScheduledFor(RETENTION_OVER.plus(365, ChronoUnit.DAYS));
        assertThat(WorkspaceRetentionPolicy.validAnnouncement(s)).isNull();
    }

    @Test
    void aLongerPeriodPublishedSinceTheEndIsKeptWhenShortenedAgain() {
        // Ended under 90; lengthened to 120 (published as "the period above"); later shortened to 60.
        List<Version> versions = List.of(
            DEFAULT,
            version(ENDED.plus(10, ChronoUnit.DAYS), 120, 14),
            version(ENDED.plus(50, ChronoUnit.DAYS), 60, 3)
        );
        assertThat(WorkspaceRetentionPolicy.rulesFor(versions, ENDED, ENDED.plus(51, ChronoUnit.DAYS))).isEqualTo(new Rules(120, 14));
    }

    @Test
    void aSubscriptionEndingLaterGetsThePeriodScheduledForThen() {
        // A yearly plan cancelled now ends after a shorter period takes effect: its emails must not promise more.
        Instant takesEffect = ENDED.plus(30, ChronoUnit.DAYS);
        List<Version> versions = List.of(DEFAULT, version(takesEffect, 60, 3));
        Instant now = takesEffect.minus(40, ChronoUnit.DAYS);
        assertThat(WorkspaceRetentionPolicy.rulesFor(versions, takesEffect.plus(5, ChronoUnit.DAYS), now)).isEqualTo(new Rules(60, 3));
    }
}
