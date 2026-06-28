package com.humano.dto.hr.requests;

import jakarta.validation.constraints.Size;

/**
 * DTO record for partially updating a reference-data value. Null fields are left unchanged.
 */
public record UpdateReferenceDataRequest(
    @Size(max = 50, message = "Code must not exceed 50 characters") String code,

    @Size(max = 255, message = "Name must not exceed 255 characters") String name,

    Boolean active,

    @Size(max = 2000, message = "Notes must not exceed 2000 characters") String notes
) {}
