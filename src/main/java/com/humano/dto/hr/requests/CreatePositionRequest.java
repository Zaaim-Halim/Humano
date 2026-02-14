package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for creating a new position.
 */
public record CreatePositionRequest(
    @NotBlank(message = "Position name is required") @Size(max = 255, message = "Position name must not exceed 255 characters") String name,

    @Size(max = 1000, message = "Description must not exceed 1000 characters") String description,

    @NotBlank(message = "Level is required") String level,

    UUID unitId,

    UUID parentPositionId
) {}
