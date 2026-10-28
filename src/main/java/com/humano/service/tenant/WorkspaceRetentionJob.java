package com.humano.service.tenant;

import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.BillingMailService;
import com.humano.service.billing.TenantAdminEmailResolver;
import com.humano.service.multitenancy.TenantProvisioningService;
import com.humano.tenancy.MultiTenantProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes workspaces whose subscription ended more than {@code humano.retention.workspace-deletion.days}
 * ago (a trial that lapsed without a plan, or a cancelled subscription), as the Terms of Service and the
 * Data Processing Addendum promise: their files and database go, the tenant row stays (marked
 * {@code DELETED}) because invoices refer to it.
 * <p>
 * Never without notice: the admin is emailed {@code warning-days} before, and a workspace is only
 * deleted once that email has gone out ({@link Tenant#getDeletionWarningSentAt()}) and its full notice
 * period has run — a warning sent late pushes the deletion back. Runs nightly; a warning that cannot
 * be sent, or a deletion that fails, is tried again the next night. Off in development, so local
 * workspaces are never wiped.
 */
@Component
public class WorkspaceRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRetentionJob.class);

    /** Subscriptions that have ended: the retention period runs from their end date. */
    private static final Set<SubscriptionStatus> ENDED = EnumSet.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED);
    /** Workspaces nobody can use any more: paused after a lapsed trial, or deactivated on cancellation. */
    private static final Set<TenantStatus> CLOSED = EnumSet.of(TenantStatus.SUSPENDED, TenantStatus.DEACTIVATED);

    enum Action {
        NONE,
        WARN,
        DELETE,
    }

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final BillingMailService mailService;
    private final TenantAdminEmailResolver adminEmailResolver;
    private final MultiTenantProperties multiTenantProperties;
    private final TransactionTemplate masterTx;
    private final boolean enabled;
    private final int days;
    private final int warningDays;

    public WorkspaceRetentionJob(
        SubscriptionRepository subscriptionRepository,
        TenantRepository tenantRepository,
        TenantProvisioningService provisioningService,
        BillingMailService mailService,
        TenantAdminEmailResolver adminEmailResolver,
        MultiTenantProperties multiTenantProperties,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Value("${humano.retention.workspace-deletion.enabled:true}") boolean enabled,
        @Value("${humano.retention.workspace-deletion.days:90}") int days,
        @Value("${humano.retention.workspace-deletion.warning-days:7}") int warningDays
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.mailService = mailService;
        this.adminEmailResolver = adminEmailResolver;
        this.multiTenantProperties = multiTenantProperties;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.enabled = enabled;
        this.days = days;
        this.warningDays = warningDays;
    }

    @Scheduled(cron = "${humano.retention.workspace-deletion.cron:0 50 3 * * *}") // nightly, 03:50
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        record Due(UUID subscriptionId, UUID tenantId, String subdomain, String name, String contact, Instant endDate, Action action) {}
        List<Due> due = masterTx.execute(tx -> {
            List<Due> found = new ArrayList<>();
            for (SubscriptionStatus status : ENDED) {
                for (Subscription s : subscriptionRepository.findByStatus(status)) {
                    Action action = decide(s, now, days, warningDays, multiTenantProperties.getPlatformTenant());
                    if (action != Action.NONE) {
                        Tenant t = s.getTenant();
                        found.add(
                            new Due(s.getId(), t.getId(), t.getSubdomain(), t.getName(), t.getDemoContactEmail(), s.getEndDate(), action)
                        );
                    }
                }
            }
            return found;
        });
        if (due == null) {
            return;
        }
        int warned = 0, deleted = 0, failed = 0;
        for (Due d : due) {
            try {
                if (d.action() == Action.WARN) {
                    String to = adminEmailResolver.resolveBillingContact(d.subdomain()).orElse(d.contact());
                    Instant sentAt = Instant.now();
                    mailService.sendWorkspaceDeletionWarning(to, d.name(), deleteOn(d.endDate(), sentAt, days, warningDays));
                    masterTx.executeWithoutResult(tx ->
                        tenantRepository
                            .findById(d.tenantId())
                            .ifPresent(t -> {
                                t.setDeletionWarningSentAt(sentAt);
                                tenantRepository.save(t);
                            })
                    );
                    warned++;
                } else if (deleteIfStillDue(d.subscriptionId(), d.tenantId())) {
                    log.warn("Workspace '{}' deleted: its subscription ended more than {} days ago", d.subdomain(), days);
                    deleted++;
                }
            } catch (Exception e) {
                log.error("Retention of workspace '{}' failed; tried again tomorrow", d.subdomain(), e);
                failed++;
            }
        }
        if (warned + deleted + failed > 0) {
            log.info("Workspace retention: {} warned, {} deleted, {} failed", warned, deleted, failed);
        }
    }

    /** Re-checks right before deleting (the workspace may have been reactivated since the scan), then deletes. */
    private boolean deleteIfStillDue(UUID subscriptionId, UUID tenantId) {
        Boolean still = masterTx.execute(tx ->
            subscriptionRepository
                .findById(subscriptionId)
                .map(s -> decide(s, Instant.now(), days, warningDays, multiTenantProperties.getPlatformTenant()) == Action.DELETE)
                .orElse(false)
        );
        if (!Boolean.TRUE.equals(still)) {
            return false;
        }
        provisioningService.deprovisionTenant(tenantId);
        masterTx.executeWithoutResult(tx ->
            tenantRepository
                .findById(tenantId)
                .ifPresent(t -> {
                    t.setStatus(TenantStatus.DELETED);
                    tenantRepository.save(t);
                })
        );
        return true;
    }

    /**
     * When a workspace warned at {@code warnedAt} is deleted: {@code days} after its subscription ended,
     * but never sooner than {@code warningDays} after the warning went out.
     */
    static Instant deleteOn(Instant endDate, Instant warnedAt, int days, int warningDays) {
        Instant byRetention = endDate.plus(days, ChronoUnit.DAYS);
        Instant byNotice = warnedAt.plus(warningDays, ChronoUnit.DAYS);
        return byNotice.isAfter(byRetention) ? byNotice : byRetention;
    }

    /**
     * What tonight's run does with the workspace of {@code s}. From {@code warningDays} before the end of
     * the retention period ({@code days} after the subscription ended), its admin is warned — once; it is
     * deleted only after that, on {@link #deleteOn}. A warning from before the subscription ended belongs
     * to an earlier lapse (the workspace was reactivated since) and does not count. Only ended
     * subscriptions of closed customer workspaces that still have a database qualify — never a demo (they
     * have their own lifecycle) or the platform's own workspace.
     */
    static Action decide(Subscription s, Instant now, int days, int warningDays, String platformTenant) {
        Tenant t = s.getTenant();
        if (
            !ENDED.contains(s.getStatus()) ||
            s.getEndDate() == null ||
            t == null ||
            t.isDemo() ||
            !CLOSED.contains(t.getStatus()) ||
            t.getDatabaseConfig() == null ||
            (platformTenant != null && platformTenant.equalsIgnoreCase(t.getSubdomain()))
        ) {
            return Action.NONE;
        }
        Instant warnedAt = t.getDeletionWarningSentAt();
        if (warnedAt == null || warnedAt.isBefore(s.getEndDate())) {
            Instant warnFrom = s.getEndDate().plus(days, ChronoUnit.DAYS).minus(warningDays, ChronoUnit.DAYS);
            return now.isBefore(warnFrom) ? Action.NONE : Action.WARN;
        }
        return now.isBefore(deleteOn(s.getEndDate(), warnedAt, days, warningDays)) ? Action.NONE : Action.DELETE;
    }
}
