package com.humano.service.billing.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO record for creating a new feature.
 */
public record CreateFeatureRequest(
    @NotBlank(message = "Feature name is required")
    @Size(min = 2, max = 100, message = "Feature name must be between 2 and 100 characters")
    String name,

    @Size(max = 4000, message = "Description cannot exceed 4000 characters") String description,

    @NotBlank(message = "Feature code is required")
    @Size(min = 2, max = 50, message = "Feature code must be between 2 and 50 characters")
    String code,

    @Size(max = 50, message = "Category cannot exceed 50 characters") String category
) {}
