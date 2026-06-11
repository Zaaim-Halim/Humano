package com.humano.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a {@code TransferWorkflowService.executeTransfer} commits.
 *
 * <p>Lets downstream concerns react to the position/department/manager/unit
 * change without coupling the workflow service to payroll, reporting, or
 * external HRIS sync. Listeners should run in
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so a payroll-side
 * failure cannot retroactively roll back an already-applied transfer.
 *
 * <h3>Tenant context</h3>
 *
 * The event self-describes its originating tenant via {@code tenantSubdomain}
 * (the same key {@code TenantContext} holds). Listeners that mutate tenant
 * data MUST set {@code TenantContext.setCurrentTenant(event.tenantSubdomain())}
 * for the duration of their work and restore the previous value in a
 * finally block — relying on the publisher's thread-local would be brittle
 * across async hops and replays.
 *
 * <p>All "old" ids are read off the employee before changes are applied;
 * any of them may be {@code null} if the employee had no value there.
 * "New" ids may be {@code null} when the corresponding attribute is
 * unchanged by this transfer.
 *
 * @param workflowId       the originating workflow instance id
 * @param tenantSubdomain  subdomain key for the tenant routing datasource
 * @param employeeId       the employee being transferred
 * @param effectiveDate    the date the transfer takes effect (from the request)
 * @param oldPositionId    position before the transfer, or {@code null}
 * @param newPositionId    position after the transfer, or {@code null} if unchanged
 * @param oldDepartmentId  department before the transfer, or {@code null}
 * @param newDepartmentId  department after the transfer, or {@code null} if unchanged
 * @param oldManagerId     manager before the transfer, or {@code null}
 * @param newManagerId     manager after the transfer, or {@code null} if unchanged
 * @param oldUnitId        organizational unit before the transfer, or {@code null}
 * @param newUnitId        organizational unit after the transfer, or {@code null} if unchanged
 * @param executedAt       wall-clock instant the transfer was executed
 */
public record TransferExecutedEvent(
    UUID workflowId,
    String tenantSubdomain,
    UUID employeeId,
    LocalDate effectiveDate,
    UUID oldPositionId,
    UUID newPositionId,
    UUID oldDepartmentId,
    UUID newDepartmentId,
    UUID oldManagerId,
    UUID newManagerId,
    UUID oldUnitId,
    UUID newUnitId,
    Instant executedAt
)
    implements TenantScopedEvent {
    public static TransferExecutedEvent of(
        UUID workflowId,
        String tenantSubdomain,
        UUID employeeId,
        LocalDate effectiveDate,
        UUID oldPositionId,
        UUID newPositionId,
        UUID oldDepartmentId,
        UUID newDepartmentId,
        UUID oldManagerId,
        UUID newManagerId,
        UUID oldUnitId,
        UUID newUnitId
    ) {
        return new TransferExecutedEvent(
            workflowId,
            tenantSubdomain,
            employeeId,
            effectiveDate,
            oldPositionId,
            newPositionId,
            oldDepartmentId,
            newDepartmentId,
            oldManagerId,
            newManagerId,
            oldUnitId,
            newUnitId,
            Instant.now()
        );
    }
}
