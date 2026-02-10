package com.humano.service.hr.dto.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning department information.
 */
public record DepartmentResponse(
    UUID id,
    String name,
    String description,
    int employeeCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
