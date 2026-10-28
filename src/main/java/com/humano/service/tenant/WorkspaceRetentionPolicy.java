package com.humano.service.tenant;

import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.RetentionPeriodVersion;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.tenant.RetentionPeriodVersionRepository;
import com.humano.tenancy.MultiTenantProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * When a workspace whose subscription ended is warned and deleted, under the retention period the
 * Terms of Service promise. One place for the rules, so the nightly {@link WorkspaceRetentionJob}, the
 * platform console and the emails and legal pages that quote the period can never disagree.
 * <p>
 * From {@code warningDays} before the end of the retention period ({@code days} after the subscription
 * ended) the admin is warned, once; the workspace is deleted only after that, on {@link #deleteOn}, and
 * never before the date the warning gave. A warning from before the subscription ended belongs to an
 * earlier lapse (the workspace was reactivated since) and does not count. Only ended subscriptions of
 * closed customer workspaces that still have a database are in scope — never a demo (they have their own
 * lifecycle) or the platform's own workspace.
 * <p>
 * The period is set in the platform console as versions, each in force from a date; before the first, the
 * configured default ({@code humano.retention.workspace-deletion.days} / {@code warning-days}) applies. A
 * subscription gets the longest period in force at any time since it ended ({@link #rulesFor}): making it
 * longer helps everyone at once, making it shorter never reaches back to what a customer was promised.
 * Whether the job runs at all ({@code enabled}) stays configuration — it is off in development.
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

    /** A retention period and its warning notice, in days. */
    public record Rules(int days, int warningDays) {}

    /** A retention period in force from {@code effectiveFrom}; {@code changedBy} is null for the configured default. */
    public record Version(Instant effectiveFrom, int days, int warningDays, String changedBy, Instant changedAt) {
        public Rules rules() {
            return new Rules(days, warningDays);
        }
    }

    private final RetentionPeriodVersionRepository repository;
    private final TransactionTemplate masterTx;
    private final boolean enabled;
    private final Rules defaults;
    private final String platformTenant;

    public WorkspaceRetentionPolicy(
        RetentionPeriodVersionRepository repository,
        MultiTenantProperties multiTenantProperties,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Value("${humano.retention.workspace-deletion.enabled:true}") boolean enabled,
        @Value("${humano.retention.workspace-deletion.days:90}") int days,
        @Value("${humano.retention.workspace-deletion.warning-days:7}") int warningDays
    ) {
        this.repository = repository;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.enabled = enabled;
        this.defaults = new Rules(days, warningDays);
        this.platformTenant = multiTenantProperties.getPlatformTenant();
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * The retention periods as now set, read once: load it per job run, request or email and pass it
     * along, so a run never mixes two settings and several instances never disagree for long.
     */
    public Schedule load() {
        List<Version> versions = new ArrayList<>();
        versions.add(new Version(Instant.EPOCH, defaults.days(), defaults.warningDays(), null, null));
        List<RetentionPeriodVersion> rows = masterTx.execute(tx -> repository.findAllByOrderByEffectiveFromAsc());
        for (RetentionPeriodVersion v : rows != null ? rows : List.<RetentionPeriodVersion>of()) {
            versions.add(new Version(v.getEffectiveFrom(), v.getDays(), v.getWarningDays(), v.getCreatedBy(), v.getCreatedAt()));
        }
        return new Schedule(versions, platformTenant);
    }

    /** The retention periods at one point in time; see {@link WorkspaceRetentionPolicy}. */
    public static final class Schedule {

        private final List<Version> versions;
        private final String platformTenant;

        Schedule(List<Version> versions, String platformTenant) {
            this.versions = List.copyOf(versions);
            this.platformTenant = platformTenant;
        }

        /** Oldest first; the first is the configured default. */
        public List<Version> versions() {
            return versions;
        }

        public Version inForce(Instant at) {
            return WorkspaceRetentionPolicy.inForce(versions, at);
        }

        /** The next change not in force yet, if one is scheduled. */
        public Optional<Version> upcoming(Instant now) {
            return versions.stream().filter(v -> v.effectiveFrom().isAfter(now)).findFirst();
        }

        /** The rules for a subscription that ended (or ends) at {@code endDate}. */
        public Rules rulesFor(Instant endDate, Instant now) {
            return WorkspaceRetentionPolicy.rulesFor(versions, endDate, now);
        }

        public boolean inScope(Subscription s) {
            return WorkspaceRetentionPolicy.inScope(s, platformTenant);
        }

        public Action decide(Subscription s, Instant now) {
            if (!inScope(s)) {
                return Action.NONE;
            }
            Rules r = rulesFor(s.getEndDate(), now);
            return WorkspaceRetentionPolicy.decide(s, now, r.days(), r.warningDays(), platformTenant);
        }

        /** When the admin of {@code s} is (or was due to be) warned. */
        public Instant warnFrom(Subscription s, Instant now) {
            Rules r = rulesFor(s.getEndDate(), now);
            return WorkspaceRetentionPolicy.warnFrom(s.getEndDate(), r.days(), r.warningDays());
        }

        /** When {@code s}'s workspace is deleted if warned at {@code warnedAt} — never before a date already announced. */
        public Instant deleteOn(Subscription s, Instant warnedAt, Instant now) {
            Rules r = rulesFor(s.getEndDate(), now);
            return latest(WorkspaceRetentionPolicy.deleteOn(s.getEndDate(), warnedAt, r.days(), r.warningDays()), validAnnouncement(s));
        }
    }

    /** The warning that counts for the current lapse, or null if none was sent for it. */
    public static Instant validWarning(Subscription s) {
        Instant warnedAt = s.getTenant().getDeletionWarningSentAt();
        return warnedAt == null || warnedAt.isBefore(s.getEndDate()) ? null : warnedAt;
    }

    /** The deletion date the current lapse's warning gave, or null. */
    static Instant validAnnouncement(Subscription s) {
        return validWarning(s) == null ? null : s.getTenant().getDeletionScheduledFor();
    }

    static Version inForce(List<Version> versions, Instant at) {
        Version current = versions.get(0);
        for (Version v : versions) {
            if (v.effectiveFrom().isAfter(at)) {
                break;
            }
            current = v;
        }
        return current;
    }

    /**
     * The longest period in force at any time since the subscription ended, field by field: what it got
     * when it ended, or any longer one since — a later shortening never takes back what was published.
     * For a subscription that has not ended yet, the period that will be in force when it does
     * (scheduled changes included), so an email never promises more than the job will apply.
     */
    static Rules rulesFor(List<Version> versions, Instant endDate, Instant now) {
        Rules atEnd = inForce(versions, endDate).rules();
        if (endDate.isAfter(now)) {
            return atEnd;
        }
        int days = atEnd.days(), warningDays = atEnd.warningDays();
        for (Version v : versions) {
            if (v.effectiveFrom().isAfter(endDate) && !v.effectiveFrom().isAfter(now)) {
                days = Math.max(days, v.days());
                warningDays = Math.max(warningDays, v.warningDays());
            }
        }
        return new Rules(days, warningDays);
    }

    static Instant warnFrom(Instant endDate, int days, int warningDays) {
        return endDate.plus(days, ChronoUnit.DAYS).minus(warningDays, ChronoUnit.DAYS);
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
        return latest(endDate.plus(days, ChronoUnit.DAYS), warnedAt.plus(warningDays, ChronoUnit.DAYS));
    }

    static Action decide(Subscription s, Instant now, int days, int warningDays, String platformTenant) {
        if (!inScope(s, platformTenant)) {
            return Action.NONE;
        }
        Instant warnedAt = validWarning(s);
        if (warnedAt == null) {
            return now.isBefore(warnFrom(s.getEndDate(), days, warningDays)) ? Action.NONE : Action.WARN;
        }
        Instant due = latest(deleteOn(s.getEndDate(), warnedAt, days, warningDays), validAnnouncement(s));
        return now.isBefore(due) ? Action.NONE : Action.DELETE;
    }

    private static Instant latest(Instant a, Instant b) {
        return b != null && b.isAfter(a) ? b : a;
    }
}
