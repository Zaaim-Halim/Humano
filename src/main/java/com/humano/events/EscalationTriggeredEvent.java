package com.humano.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a workflow deadline is escalated to the next level.
 *
 * <p>Listeners can side-effect on this — notify managers, dispatch reminders, surface
 * dashboards — without {@code DeadlineMonitorService} taking a hard dependency on every
 * downstream channel. Per invariant I5, listeners that send mail / push / etc. should be
 * {@code @Async} so the scheduler's tick stays bounded.
 *
 * <p>Implements {@link TenantScopedEvent}: this event originates inside a tenant DB
 * context, so the listener must route to that tenant via {@code event.tenantSubdomain()}.
 *
 * @param deadlineId       primary key of the {@code WorkflowDeadline} that escalated
 * @param tenantSubdomain  subdomain key for the originating tenant
 * @param workflowId       parent {@code WorkflowInstance} id
 * @param escalationLevel  new level after escalation (1-based; capped by
 *                         {@code humano.workflow.max-escalation-level})
 * @param assigneeId       current assignee at the time of escalation, or {@code null}
 *                         if the deadline has no assignee
 * @param escalatedAt      wall-clock instant the escalation was committed
 */
public record EscalationTriggeredEvent(
    UUID deadlineId,
    String tenantSubdomain,
    UUID workflowId,
    int escalationLevel,
    UUID assigneeId,
    Instant escalatedAt
)
    implements TenantScopedEvent {
    public static EscalationTriggeredEvent of(
        UUID deadlineId,
        String tenantSubdomain,
        UUID workflowId,
        int escalationLevel,
        UUID assigneeId
    ) {
        return new EscalationTriggeredEvent(deadlineId, tenantSubdomain, workflowId, escalationLevel, assigneeId, Instant.now());
    }
}
