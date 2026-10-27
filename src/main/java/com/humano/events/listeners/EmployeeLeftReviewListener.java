package com.humano.events.listeners;

import com.humano.events.EmployeeLeftEvent;
import com.humano.service.hr.workflow.PerformanceReviewCycleService;
import com.humano.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Winds up a leaver's part in running review cycles once their leaving is committed (see
 * {@link PerformanceReviewCycleService#onEmployeeLeft}). AFTER_COMMIT, in a fresh transaction, so a
 * failure here is logged and never undoes the termination; {@code fallbackExecution} covers a
 * publisher without a transaction.
 */
@Component
public class EmployeeLeftReviewListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLeftReviewListener.class);

    private final PerformanceReviewCycleService reviewCycleService;

    public EmployeeLeftReviewListener(PerformanceReviewCycleService reviewCycleService) {
        this.reviewCycleService = reviewCycleService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleEmployeeLeft(EmployeeLeftEvent event) {
        if (event.tenantSubdomain() == null) {
            log.warn("EmployeeLeftEvent for employee {} has no tenantSubdomain; reviews left as they are", event.employeeId());
            return;
        }
        String previousTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(event.tenantSubdomain());
            reviewCycleService.onEmployeeLeft(event.employeeId(), event.lastDay());
        } catch (Exception e) {
            // The leaving is already committed: log so HR can tidy the cycle by hand, do not rethrow.
            log.error("Failed to update review cycles for leaver {} (tenant {})", event.employeeId(), event.tenantSubdomain(), e);
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }
}
