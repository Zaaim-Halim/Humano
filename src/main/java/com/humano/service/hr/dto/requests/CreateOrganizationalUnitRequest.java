package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for creating a new organizational unit.
 */
public record CreateOrganizationalUnitRequest(
    @NotBlank(message = "Name is required") @Size(max = 255, message = "Name must not exceed 255 characters") String name,

    @NotNull(message = "Unit type is required") OrganizationalUnitType type,

    UUID parentUnitId,

    UUID managerId
) {}
