package com.humano.dto.platform.compliance;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/platform/compliance/retention}: the workspace retention period in force, a shorter one
 * scheduled (if any), past changes newest first, and the limits a change must respect. {@code enabled}
 * false means the deletion job is switched off by configuration in this environment.
 */
public record RetentionSettings(
    boolean enabled,
    Period current,
    Period upcoming,
    List<Period> history,
    int minDays,
    int maxDays,
    int maxWarningDays,
    int shorteningNoticeDays
) {
    /** A retention period in force from {@code effectiveFrom}; {@code isDefault} for the configured one (never changed). */
    public record Period(Instant effectiveFrom, int days, int warningDays, String changedBy, Instant changedAt, boolean isDefault) {}
}
