package com.humano.events.listeners;

import com.humano.config.multitenancy.TenantContext;
import com.humano.events.TransferExecutedEvent;
import com.humano.service.payroll.CompensationService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the active {@code Compensation} row's {@code position} reference in sync
 * after a position-changing transfer commits.
 *
 * <h3>Why AFTER_COMMIT, not {@code @Async}</h3>
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: the transfer is already durable. Payroll sync runs in a
 *       fresh transaction (Compensation methods are {@code @Transactional}); a sync
 *       failure is logged and surfaces operationally, but does NOT roll back an
 *       already-applied transfer.</li>
 *   <li><b>Not {@code @Async}</b>: the project's existing async listeners fire BEFORE
 *       the publisher's transaction commits. For "react to a committed transfer"
 *       semantics that's the wrong ordering — a rollback would leave a phantom
 *       compensation row.</li>
 * </ul>
 *
 * <h3>Tenant routing</h3>
 *
 * Compensation lives in the tenant DB, so {@code TenantRoutingDataSource} must see
 * the right tenant in {@code TenantContext}. The event carries the originating
 * tenant subdomain at publish time. The listener sets it before any tenant-scoped
 * call and restores the previous value in a finally block — this remains correct
 * even if a future maintainer flips the listener to {@code @Async} or the executor
 * is misconfigured without {@code TenantAwareTaskDecorator}.
 */
@Component
public class TransferPayrollSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TransferPayrollSyncListener.class);

    private final CompensationService compensationService;

    public TransferPayrollSyncListener(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransferExecuted(TransferExecutedEvent event) {
        if (event.newPositionId() == null || Objects.equals(event.oldPositionId(), event.newPositionId())) {
            return;
        }
        if (event.tenantSubdomain() == null) {
            log.warn("TransferExecutedEvent for workflow {} has no tenantSubdomain; skipping compensation sync", event.workflowId());
            return;
        }

        String previousTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(event.tenantSubdomain());
            compensationService.applyPositionChange(event.employeeId(), event.newPositionId(), event.effectiveDate());
        } catch (Exception e) {
            // Transfer is already committed — log so ops can reconcile, do not rethrow.
            log.error(
                "Failed to sync compensation for transfer {} (employee {}, tenant {})",
                event.workflowId(),
                event.employeeId(),
                event.tenantSubdomain(),
                e
            );
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }
}
