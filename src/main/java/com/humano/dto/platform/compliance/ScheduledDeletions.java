package com.humano.dto.platform.compliance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/platform/compliance/deletions}: the workspaces under the retention period — their
 * subscription ended — and when each is warned and deleted. {@code enabled} false means the nightly job
 * is switched off by configuration and nothing will happen.
 */
public record ScheduledDeletions(boolean enabled, int days, int warningDays, List<Item> items) {
    public enum State {
        /** Before the warning period: nothing to do yet. */
        WAITING,
        /** The warning period started but the email has not gone out (it is retried nightly). */
        WARNING_DUE,
        /** Warned; deleted on {@code deleteOn}. */
        WARNED,
    }

    /**
     * One workspace. {@code deleteOn} is final once warned; before that it is the earliest date (the
     * retention period's end, later if the warning goes out late). {@code retentionDays} is this
     * workspace's period — the longer of the one in force when it ended and now. {@code warningOverdue}: due for more
     * than a day and still not sent — usually mail that is not configured.
     */
    public record Item(
        UUID tenantId,
        String name,
        String subdomain,
        String tenantStatus,
        String subscriptionStatus,
        Instant endedAt,
        int retentionDays,
        Instant warnFrom,
        Instant warningSentAt,
        Instant deleteOn,
        State state,
        boolean warningOverdue
    ) {}
}
