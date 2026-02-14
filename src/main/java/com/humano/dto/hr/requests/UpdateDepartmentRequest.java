package com.humano.dto.hr.requests;

import jakarta.validation.constraints.Size;

/**
 * DTO record for updating an existing department.
 */
public record UpdateDepartmentRequest(
    @Size(max = 255, message = "Department name must not exceed 255 characters") String name,

    @Size(max = 500, message = "Description must not exceed 500 characters") String description
) {}
