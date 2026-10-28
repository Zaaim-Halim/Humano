package com.humano.dto.billing.responses;

import java.time.Instant;

/**
 * The workspace's acceptance of the Terms of Service and DPA. {@code applies} is false for workspaces
 * that are not under them (demos, the platform's own); {@code upToDate} whether the accepted version is
 * the current one.
 */
public record TermsStatusResponse(
    boolean applies,
    String currentVersion,
    String acceptedVersion,
    Instant acceptedAt,
    String acceptedBy,
    boolean upToDate
) {}
