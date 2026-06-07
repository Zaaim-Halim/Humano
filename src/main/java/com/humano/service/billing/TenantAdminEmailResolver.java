package com.humano.service.billing;

import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.shared.User;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import java.util.Comparator;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P4.3 — Resolves the billing-contact email for a tenant.
 * <p>
 * Billing emails are scheduled / event-driven and run with NO tenant context on
 * the calling thread (master-DB-scoped transactions or pure async event flows).
 * This resolver crosses the master → tenant boundary deliberately: it switches
 * {@link TenantContext} to the supplied subdomain, queries the seeded admin
 * user via {@link UserRepository#findActivatedByAuthority}, returns the email,
 * then restores the prior tenant context in a {@code finally}.
 * <p>
 * Reads are wrapped in a {@code @Transactional(readOnly = true,
 * transactionManager = "tenantTransactionManager")}-equivalent via
 * {@link TransactionTemplate} so the existing routing data source picks the
 * right Hikari pool.
 * <p>
 * <b>Limitation.</b> Tenants without an ACTIVE admin row (provisioning
 * crashed mid-flow, admin manually deactivated) get {@link Optional#empty()}
 * and the caller logs + skips the email — no exception. Operators see the
 * skip in the log and can repair the admin row.
 */
@Service
public class TenantAdminEmailResolver {

    private static final Logger LOG = LoggerFactory.getLogger(TenantAdminEmailResolver.class);

    private final UserRepository userRepository;
    private final TransactionTemplate tenantTx;

    public TenantAdminEmailResolver(
        UserRepository userRepository,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.userRepository = userRepository;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
        this.tenantTx.setReadOnly(true);
    }

    public Optional<String> resolveBillingContact(String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            return Optional.empty();
        }
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(subdomain);
        try {
            return tenantTx.execute(status ->
                userRepository
                    .findActivatedByAuthority(AuthoritiesConstants.ADMIN)
                    .stream()
                    .min(Comparator.comparing(User::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(User::getEmail)
            );
        } catch (RuntimeException e) {
            LOG.warn("Failed to resolve admin email for tenant '{}': {}", subdomain, e.getMessage());
            return Optional.empty();
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }
}
