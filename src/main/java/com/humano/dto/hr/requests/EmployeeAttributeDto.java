package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A single custom employee attribute (key/value) in an update request.
 */
public record EmployeeAttributeDto(
    @NotBlank(message = "Attribute key is required") @Size(max = 255, message = "Attribute key must not exceed 255 characters") String key,
    @NotNull(message = "Attribute value is required")
    @Size(max = 1000, message = "Attribute value must not exceed 1000 characters")
    String value
) {}
