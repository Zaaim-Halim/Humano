package com.humano.dto.hr.responses;

import com.humano.domain.shared.AbstractReferenceData;
import java.util.UUID;

/**
 * Minimal embedded reference to a tenant-configurable reference-data row (employment type, job
 * grade, marital status, …). On reads it carries {@code id} + {@code code} + {@code name} so the
 * client can render a label without a second fetch; on writes only {@code id} is read.
 */
public record ReferenceDataRef(UUID id, String code, String name) {
    public static ReferenceDataRef of(AbstractReferenceData e) {
        return e == null ? null : new ReferenceDataRef(e.getId(), e.getCode(), e.getName());
    }
}
