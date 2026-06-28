package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO record for creating a tenant-configurable reference-data value
 * (employment type, job grade, job level, employee category, marital status, termination reason).
 */
public record CreateReferenceDataRequest(
    @NotBlank(message = "Code is required") @Size(max = 50, message = "Code must not exceed 50 characters") String code,

    @NotBlank(message = "Name is required") @Size(max = 255, message = "Name must not exceed 255 characters") String name,

    Boolean active,

    @Size(max = 2000, message = "Notes must not exceed 2000 characters") String notes
) {}
