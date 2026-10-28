package com.humano.dto.platform.compliance;

import java.time.Instant;

/**
 * {@code GET /api/public/legal/retention}: the retention period the legal pages, the website and the app
 * quote, and a shorter one scheduled (null if none) so the Terms can announce it ahead of time.
 */
public record PublicRetention(int days, int warningDays, Upcoming upcoming) {
    public record Upcoming(Instant effectiveFrom, int days, int warningDays) {}
}
