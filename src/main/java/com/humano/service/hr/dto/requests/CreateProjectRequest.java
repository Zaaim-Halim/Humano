package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * DTO record for creating a new project.
 */
public record CreateProjectRequest(
    @NotBlank(message = "Project name is required") @Size(max = 255, message = "Project name must not exceed 255 characters") String name,

    @Size(max = 1000, message = "Description must not exceed 1000 characters") String description,

    LocalDateTime startTime,

    LocalDateTime endTime
) {}
