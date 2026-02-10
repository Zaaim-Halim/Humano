package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for updating an existing position.
 */
public record UpdatePositionRequest(
    @Size(max = 255, message = "Position name must not exceed 255 characters") String name,

    @Size(max = 1000, message = "Description must not exceed 1000 characters") String description,

    String level,

    UUID unitId,

    UUID parentPositionId
) {}
