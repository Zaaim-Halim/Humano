package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO record for creating a new skill.
 */
public record CreateSkillRequest(
    @NotBlank(message = "Skill name is required") @Size(max = 255, message = "Skill name must not exceed 255 characters") String name,

    @Size(max = 2000, message = "Description must not exceed 2000 characters") String description,

    String category,

    Boolean requiresCertification
) {}
