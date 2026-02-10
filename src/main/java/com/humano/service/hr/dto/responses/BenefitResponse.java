package com.humano.service.hr.dto.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning benefit information.
 */
public record BenefitResponse(
    UUID id,
    String name,
    BigDecimal amount,
    String description,
    int employeeCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
