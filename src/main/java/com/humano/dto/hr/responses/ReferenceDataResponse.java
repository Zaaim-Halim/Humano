package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a reference-data value.
 */
public record ReferenceDataResponse(
    UUID id,
    String code,
    String name,
    Boolean active,
    String notes,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
