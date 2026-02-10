package com.humano.service.hr.dto.responses;

import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning organizational unit information.
 */
public record OrganizationalUnitResponse(
    UUID id,
    String name,
    OrganizationalUnitType type,
    String path,
    UUID parentUnitId,
    String parentUnitName,
    UUID managerId,
    String managerName,
    int employeeCount,
    int subUnitCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
