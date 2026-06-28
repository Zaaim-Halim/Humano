package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeLanguage record.
 */
public record EmployeeLanguageResponse(
    UUID id,
    UUID employeeId,
    String language,
    String reading,
    String writing,
    String speaking,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
