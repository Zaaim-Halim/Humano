package com.humano.service.billing;

import com.humano.config.Constants;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.responses.TermsStatusResponse;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.tenancy.MultiTenantProperties;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The workspace's acceptance of the Terms of Service and Data Processing Addendum: recorded at sign-up,
 * and again by a billing admin when they change ({@link Constants#TERMS_VERSION}). Accepting is never
 * required to keep using the workspace — under the Terms, changes apply from the next renewal and a
 * customer who disagrees may cancel — but it is recorded, with who and when.
 */
@Service
public class TermsAcceptanceService {

    private static final Logger log = LoggerFactory.getLogger(TermsAcceptanceService.class);
    private static final String ENTITY_NAME = "terms";

    private final TenantRepository tenantRepository;
    private final MultiTenantProperties multiTenantProperties;
    private final TransactionTemplate masterTx;

    public TermsAcceptanceService(
        TenantRepository tenantRepository,
        MultiTenantProperties multiTenantProperties,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.multiTenantProperties = multiTenantProperties;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
    }

    public TermsStatusResponse status(UUID tenantId) {
        return masterTx.execute(tx -> toStatus(load(tenantId)));
    }

    /** Records that {@code login} accepted the current Terms for the workspace; refuses any other version. */
    public TermsStatusResponse accept(UUID tenantId, String version, String login) {
        if (!Constants.TERMS_VERSION.equals(version)) {
            throw new BadRequestAlertException("The terms have changed since this page was loaded", ENTITY_NAME, "termsoutdated");
        }
        return masterTx.execute(tx -> {
            Tenant t = load(tenantId);
            if (!applies(t, multiTenantProperties.getPlatformTenant())) {
                throw new BadRequestAlertException("This workspace is not under the customer terms", ENTITY_NAME, "termsnotapplicable");
            }
            t.setTermsVersion(version);
            t.setTermsAcceptedAt(Instant.now());
            t.setTermsAcceptedBy(login);
            tenantRepository.save(t);
            log.info("Workspace '{}' accepted terms {} ({})", t.getSubdomain(), version, login);
            return toStatus(t);
        });
    }

    private Tenant load(UUID tenantId) {
        return tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new BadRequestAlertException("Unknown workspace", ENTITY_NAME, "notfound"));
    }

    private TermsStatusResponse toStatus(Tenant t) {
        boolean applies = applies(t, multiTenantProperties.getPlatformTenant());
        return new TermsStatusResponse(
            applies,
            Constants.TERMS_VERSION,
            t.getTermsVersion(),
            t.getTermsAcceptedAt(),
            t.getTermsAcceptedBy(),
            Constants.TERMS_VERSION.equals(t.getTermsVersion())
        );
    }

    /** Customer workspaces only: demos and the platform's own workspace are not under the customer terms. */
    static boolean applies(Tenant t, String platformTenant) {
        return !t.isDemo() && (platformTenant == null || !platformTenant.equalsIgnoreCase(t.getSubdomain()));
    }
}
