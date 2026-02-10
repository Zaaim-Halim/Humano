package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for updating an existing organizational unit.
 */
public record UpdateOrganizationalUnitRequest(
    @Size(max = 255, message = "Unit name must not exceed 255 characters") String name,

    OrganizationalUnitType type,

    UUID parentUnitId,

    UUID managerId
) {}
