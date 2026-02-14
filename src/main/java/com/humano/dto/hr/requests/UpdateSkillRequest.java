package com.humano.dto.hr.requests;

import jakarta.validation.constraints.Size;

/**
 * DTO record for updating an existing skill.
 */
public record UpdateSkillRequest(
    @Size(max = 255, message = "Skill name must not exceed 255 characters") String name,

    @Size(max = 2000, message = "Description must not exceed 2000 characters") String description,

    String category,

    Boolean requiresCertification
) {}
