package com.humano.dto.platform.compliance;

import com.humano.domain.tenant.SubprocessorNotice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A sub-processor notice as sent: to how many workspaces, how many had no address, and how many failed. */
public record SubprocessorNoticeResponse(
    UUID id,
    String name,
    String purpose,
    String dataCategories,
    String location,
    LocalDate effectiveFrom,
    Instant sentAt,
    String sentBy,
    int recipientCount,
    int missingCount,
    int failedCount
) {
    public static SubprocessorNoticeResponse of(SubprocessorNotice n) {
        return new SubprocessorNoticeResponse(
            n.getId(),
            n.getName(),
            n.getPurpose(),
            n.getDataCategories(),
            n.getLocation(),
            n.getEffectiveFrom(),
            n.getSentAt(),
            n.getSentBy(),
            n.getRecipientCount(),
            n.getMissingCount(),
            n.getFailedCount()
        );
    }
}
