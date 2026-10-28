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
import com.humano.service.tenant.WorkspaceRetentionPolicy.Action;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Rules;
import com.humano.service.tenant.WorkspaceRetentionPolicy.Schedule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * {@code DELETED}) because invoices refer to it. The rules are {@link WorkspaceRetentionPolicy}'s.
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

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final BillingMailService mailService;
    private final TenantAdminEmailResolver adminEmailResolver;
    private final WorkspaceRetentionPolicy policy;
    private final TransactionTemplate masterTx;

    public WorkspaceRetentionJob(
        SubscriptionRepository subscriptionRepository,
        TenantRepository tenantRepository,
        TenantProvisioningService provisioningService,
        BillingMailService mailService,
        TenantAdminEmailResolver adminEmailResolver,
        WorkspaceRetentionPolicy policy,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.mailService = mailService;
        this.adminEmailResolver = adminEmailResolver;
        this.policy = policy;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
    }

    @Scheduled(cron = "${humano.retention.workspace-deletion.cron:0 50 3 * * *}") // nightly, 03:50
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        if (!policy.enabled()) {
            return;
        }
        Instant now = Instant.now();
        Schedule schedule = policy.load();
        record Due(
            UUID subscriptionId,
            UUID tenantId,
            String subdomain,
            String name,
            String contact,
            Instant endDate,
            Rules rules,
            Action action
        ) {}
        List<Due> due = masterTx.execute(tx -> {
            List<Due> found = new ArrayList<>();
            for (SubscriptionStatus status : List.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED)) {
                for (Subscription s : subscriptionRepository.findByStatus(status)) {
                    Action action = schedule.decide(s, now);
                    if (action != Action.NONE) {
                        Tenant t = s.getTenant();
                        Rules rules = schedule.rulesFor(s.getEndDate(), now);
                        found.add(
                            new Due(
                                s.getId(),
                                t.getId(),
                                t.getSubdomain(),
                                t.getName(),
                                t.getDemoContactEmail(),
                                s.getEndDate(),
                                rules,
                                action
                            )
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
                    Instant deleteOn = WorkspaceRetentionPolicy.deleteOn(d.endDate(), sentAt, d.rules().days(), d.rules().warningDays());
                    mailService.sendWorkspaceDeletionWarning(to, d.name(), deleteOn, d.rules().days());
                    // The date the email gave is kept: the workspace is never deleted before it, even if the period is shortened.
                    masterTx.executeWithoutResult(tx ->
                        tenantRepository
                            .findById(d.tenantId())
                            .ifPresent(t -> {
                                t.setDeletionWarningSentAt(sentAt);
                                t.setDeletionScheduledFor(deleteOn);
                                tenantRepository.save(t);
                            })
                    );
                    warned++;
                } else if (deleteIfStillDue(d.subscriptionId(), d.tenantId())) {
                    log.warn("Workspace '{}' deleted: its subscription ended more than {} days ago", d.subdomain(), d.rules().days());
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
            subscriptionRepository.findById(subscriptionId).map(s -> policy.load().decide(s, Instant.now()) == Action.DELETE).orElse(false)
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
}
