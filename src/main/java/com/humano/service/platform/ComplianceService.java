package com.humano.service.platform;

import com.humano.config.Constants;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.SubprocessorNotice;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.platform.compliance.ScheduledDeletions;
import com.humano.dto.platform.compliance.ScheduledDeletions.Item;
import com.humano.dto.platform.compliance.ScheduledDeletions.State;
import com.humano.dto.platform.compliance.SubprocessorNoticeRequest;
import com.humano.dto.platform.compliance.SubprocessorNoticeResponse;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.SubprocessorNoticeRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.security.SecurityUtils;
import com.humano.service.billing.BillingMailService;
import com.humano.service.billing.TenantAdminEmailResolver;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.tenant.WorkspaceRetentionPolicy;
import com.humano.tenancy.MultiTenantProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the platform console shows and does to keep the data-protection promises: the workspaces
 * scheduled for deletion under the retention period, and the notices of new sub-processors sent to
 * every customer ahead of time.
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);
    private static final String ENTITY_NAME = "subprocessorNotice";

    /** Workspaces that hold customer data: set up, whether in use, paused or deactivated (still in retention). */
    private static final Set<TenantStatus> HOLDS_DATA = EnumSet.of(
        TenantStatus.ACTIVE,
        TenantStatus.SUSPENDED,
        TenantStatus.DEACTIVATED,
        TenantStatus.MIGRATION_FAILED
    );

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SubprocessorNoticeRepository noticeRepository;
    private final WorkspaceRetentionPolicy retentionPolicy;
    private final BillingMailService mailService;
    private final TenantAdminEmailResolver adminEmailResolver;
    private final MultiTenantProperties multiTenantProperties;
    private final TransactionTemplate masterTx;

    public ComplianceService(
        SubscriptionRepository subscriptionRepository,
        TenantRepository tenantRepository,
        SubprocessorNoticeRepository noticeRepository,
        WorkspaceRetentionPolicy retentionPolicy,
        BillingMailService mailService,
        TenantAdminEmailResolver adminEmailResolver,
        MultiTenantProperties multiTenantProperties,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.noticeRepository = noticeRepository;
        this.retentionPolicy = retentionPolicy;
        this.mailService = mailService;
        this.adminEmailResolver = adminEmailResolver;
        this.multiTenantProperties = multiTenantProperties;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
    }

    /** The workspaces under the retention period, soonest deletion first. */
    public ScheduledDeletions scheduledDeletions() {
        Instant now = Instant.now();
        List<Item> items = masterTx.execute(tx -> {
            List<Item> found = new ArrayList<>();
            for (SubscriptionStatus status : List.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED)) {
                for (Subscription s : subscriptionRepository.findByStatus(status)) {
                    if (retentionPolicy.inScope(s)) {
                        found.add(item(s, now));
                    }
                }
            }
            found.sort(Comparator.comparing(Item::deleteOn));
            return found;
        });
        return new ScheduledDeletions(
            retentionPolicy.enabled(),
            retentionPolicy.days(),
            retentionPolicy.warningDays(),
            items != null ? items : List.of()
        );
    }

    private Item item(Subscription s, Instant now) {
        Tenant t = s.getTenant();
        Instant warnFrom = retentionPolicy.warnFrom(s.getEndDate());
        Instant warned = retentionPolicy.validWarning(s);
        State state;
        Instant deleteOn;
        if (warned != null) {
            state = State.WARNED;
            deleteOn = retentionPolicy.deleteOn(s.getEndDate(), warned);
        } else {
            state = now.isBefore(warnFrom) ? State.WAITING : State.WARNING_DUE;
            // Not warned yet: the earliest it can go, if the warning went out now (or when it is due).
            deleteOn = retentionPolicy.deleteOn(s.getEndDate(), now.isBefore(warnFrom) ? warnFrom : now);
        }
        // The job runs nightly: due for more than a day means at least one run failed to send it.
        // Not when the job is switched off: then nothing is sent, by design.
        boolean overdue = retentionPolicy.enabled() && state == State.WARNING_DUE && now.isAfter(warnFrom.plus(1, ChronoUnit.DAYS));
        return new Item(
            t.getId(),
            t.getName(),
            t.getSubdomain(),
            t.getStatus().name(),
            s.getStatus().name(),
            s.getEndDate(),
            warnFrom,
            warned,
            deleteOn,
            state,
            overdue
        );
    }

    /** Notices sent so far, newest first. */
    public List<SubprocessorNoticeResponse> notices() {
        List<SubprocessorNoticeResponse> list = masterTx.execute(tx ->
            noticeRepository.findAllByOrderBySentAtDesc().stream().map(SubprocessorNoticeResponse::of).toList()
        );
        return list != null ? list : List.of();
    }

    /** How many workspaces a notice would go to now (the console shows it before sending). */
    public int noticeRecipientCount() {
        Integer count = masterTx.execute(tx -> recipients().size());
        return count != null ? count : 0;
    }

    /**
     * Emails the notice to the contact of every workspace that holds customer data, and records it. The
     * sub-processor must start no sooner than {@link Constants#SUBPROCESSOR_NOTICE_DAYS} from today.
     */
    public SubprocessorNoticeResponse sendSubprocessorNotice(SubprocessorNoticeRequest request) {
        LocalDate earliest = LocalDate.now(ZoneOffset.UTC).plusDays(Constants.SUBPROCESSOR_NOTICE_DAYS);
        if (request.effectiveFrom().isBefore(earliest)) {
            throw new BadRequestAlertException(
                "A new sub-processor needs " + Constants.SUBPROCESSOR_NOTICE_DAYS + " days' notice",
                ENTITY_NAME,
                "noticetooshort"
            );
        }
        record Recipient(String subdomain, String name, String contact) {}
        List<Recipient> recipients = masterTx.execute(tx ->
            recipients().stream().map(t -> new Recipient(t.getSubdomain(), t.getName(), t.getDemoContactEmail())).toList()
        );
        // One at a time and synchronously, so the record says how many really went out; one workspace
        // failing never stops the others, and the record is saved whatever happens.
        int sent = 0, missing = 0, failed = 0;
        for (Recipient r : recipients != null ? recipients : List.<Recipient>of()) {
            try {
                String to = adminEmailResolver.resolveBillingContact(r.subdomain()).orElse(r.contact());
                if (to == null || to.isBlank()) {
                    log.warn("Sub-processor notice: workspace '{}' has no contact address", r.subdomain());
                    missing++;
                    continue;
                }
                mailService.sendSubprocessorNotice(
                    to,
                    r.name(),
                    request.name(),
                    request.purpose(),
                    request.dataCategories(),
                    request.location(),
                    request.effectiveFrom()
                );
                sent++;
            } catch (Exception e) {
                log.error("Sub-processor notice to workspace '{}' could not be sent", r.subdomain(), e);
                failed++;
            }
        }
        SubprocessorNotice notice = new SubprocessorNotice();
        notice.setName(request.name().trim());
        notice.setPurpose(request.purpose().trim());
        notice.setDataCategories(request.dataCategories().trim());
        notice.setLocation(request.location().trim());
        notice.setEffectiveFrom(request.effectiveFrom());
        notice.setSentAt(Instant.now());
        notice.setSentBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        notice.setRecipientCount(sent);
        notice.setMissingCount(missing);
        notice.setFailedCount(failed);
        SubprocessorNotice saved = masterTx.execute(tx -> noticeRepository.save(notice));
        log.info(
            "Sub-processor notice for '{}' sent to {} workspace(s); {} without an address, {} failed",
            request.name(),
            sent,
            missing,
            failed
        );
        return SubprocessorNoticeResponse.of(saved != null ? saved : notice);
    }

    private List<Tenant> recipients() {
        String platform = multiTenantProperties.getPlatformTenant();
        return tenantRepository.findAll().stream().filter(t -> receivesNotices(t, platform)).toList();
    }

    /**
     * Whether a workspace's customer is told of a new sub-processor: every customer whose data we hold —
     * paused and deactivated workspaces still in retention included — but not demos or our own workspace.
     */
    static boolean receivesNotices(Tenant t, String platformTenant) {
        return (
            !t.isDemo() &&
            HOLDS_DATA.contains(t.getStatus()) &&
            t.getDatabaseConfig() != null &&
            (platformTenant == null || !platformTenant.equalsIgnoreCase(t.getSubdomain()))
        );
    }
}
