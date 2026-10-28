package com.humano.service.tenant;

import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.tenancy.MultiTenantProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * When a workspace whose subscription ended is warned and deleted, under the retention period the
 * Terms of Service promise ({@code humano.retention.workspace-deletion.*}). One place for the rules, so
 * the nightly {@link WorkspaceRetentionJob} and the platform console's list of scheduled deletions can
 * never disagree.
 * <p>
 * From {@code warningDays} before the end of the retention period ({@code days} after the subscription
 * ended) the admin is warned, once; the workspace is deleted only after that, on {@link #deleteOn}. A
 * warning from before the subscription ended belongs to an earlier lapse (the workspace was reactivated
 * since) and does not count. Only ended subscriptions of closed customer workspaces that still have a
 * database are in scope — never a demo (they have their own lifecycle) or the platform's own workspace.
 */
@Component
public class WorkspaceRetentionPolicy {

    /** Subscriptions that have ended: the retention period runs from their end date. */
    private static final Set<SubscriptionStatus> ENDED = EnumSet.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED);
    /** Workspaces nobody can use any more: paused after a lapsed trial, or deactivated on cancellation. */
    private static final Set<TenantStatus> CLOSED = EnumSet.of(TenantStatus.SUSPENDED, TenantStatus.DEACTIVATED);

    public enum Action {
        NONE,
        WARN,
        DELETE,
    }

    private final boolean enabled;
    private final int days;
    private final int warningDays;
    private final String platformTenant;

    public WorkspaceRetentionPolicy(
        MultiTenantProperties multiTenantProperties,
        @Value("${humano.retention.workspace-deletion.enabled:true}") boolean enabled,
        @Value("${humano.retention.workspace-deletion.days:90}") int days,
        @Value("${humano.retention.workspace-deletion.warning-days:7}") int warningDays
    ) {
        this.enabled = enabled;
        this.days = days;
        this.warningDays = warningDays;
        this.platformTenant = multiTenantProperties.getPlatformTenant();
    }

    public boolean enabled() {
        return enabled;
    }

    public int days() {
        return days;
    }

    public int warningDays() {
        return warningDays;
    }

    /** Whether the workspace of {@code s} is one the retention period applies to at all. */
    public boolean inScope(Subscription s) {
        return inScope(s, platformTenant);
    }

    public Action decide(Subscription s, Instant now) {
        return decide(s, now, days, warningDays, platformTenant);
    }

    /** The warning that counts for the current lapse, or null if none was sent for it. */
    public Instant validWarning(Subscription s) {
        Instant warnedAt = s.getTenant().getDeletionWarningSentAt();
        return warnedAt == null || warnedAt.isBefore(s.getEndDate()) ? null : warnedAt;
    }

    /** When the admin is (or was due to be) warned. */
    public Instant warnFrom(Instant endDate) {
        return endDate.plus(days, ChronoUnit.DAYS).minus(warningDays, ChronoUnit.DAYS);
    }

    /** When the workspace is deleted, given when it was warned (now, for a warning not sent yet). */
    public Instant deleteOn(Instant endDate, Instant warnedAt) {
        return deleteOn(endDate, warnedAt, days, warningDays);
    }

    static boolean inScope(Subscription s, String platformTenant) {
        Tenant t = s.getTenant();
        return (
            ENDED.contains(s.getStatus()) &&
            s.getEndDate() != null &&
            t != null &&
            !t.isDemo() &&
            CLOSED.contains(t.getStatus()) &&
            t.getDatabaseConfig() != null &&
            (platformTenant == null || !platformTenant.equalsIgnoreCase(t.getSubdomain()))
        );
    }

    /** {@code days} after the subscription ended, but never sooner than {@code warningDays} after the warning went out. */
    static Instant deleteOn(Instant endDate, Instant warnedAt, int days, int warningDays) {
        Instant byRetention = endDate.plus(days, ChronoUnit.DAYS);
        Instant byNotice = warnedAt.plus(warningDays, ChronoUnit.DAYS);
        return byNotice.isAfter(byRetention) ? byNotice : byRetention;
    }

    static Action decide(Subscription s, Instant now, int days, int warningDays, String platformTenant) {
        if (!inScope(s, platformTenant)) {
            return Action.NONE;
        }
        Instant warnedAt = s.getTenant().getDeletionWarningSentAt();
        if (warnedAt == null || warnedAt.isBefore(s.getEndDate())) {
            Instant warnFrom = s.getEndDate().plus(days, ChronoUnit.DAYS).minus(warningDays, ChronoUnit.DAYS);
            return now.isBefore(warnFrom) ? Action.NONE : Action.WARN;
        }
        return now.isBefore(deleteOn(s.getEndDate(), warnedAt, days, warningDays)) ? Action.NONE : Action.DELETE;
    }
}
