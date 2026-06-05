package com.humano.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a workflow deadline is escalated to the next level .
 *
 * <p>Listeners can side-effect on this — notify managers, dispatch reminders, surface
 * dashboards — without {@code DeadlineMonitorService} taking a hard dependency on every
 * downstream channel. Per invariant I5, listeners that send mail / push / etc. should be
 * {@code @Async} so the scheduler's tick stays bounded.
 *
 * @param deadlineId       primary key of the {@code WorkflowDeadline} that escalated
 * @param workflowId       parent {@code WorkflowInstance} id
 * @param escalationLevel  new level after escalation (1-based; capped by
 *                         {@code humano.workflow.max-escalation-level})
 * @param assigneeId       current assignee at the time of escalation, or {@code null}
 *                         if the deadline has no assignee
 * @param escalatedAt      wall-clock instant the escalation was committed
 */
public record EscalationTriggeredEvent(UUID deadlineId, UUID workflowId, int escalationLevel, UUID assigneeId, Instant escalatedAt) {
    public static EscalationTriggeredEvent of(UUID deadlineId, UUID workflowId, int escalationLevel, UUID assigneeId) {
        return new EscalationTriggeredEvent(deadlineId, workflowId, escalationLevel, assigneeId, Instant.now());
    }
}
