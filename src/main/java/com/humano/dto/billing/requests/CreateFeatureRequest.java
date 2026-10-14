package com.humano.dto.billing.requests;

import com.humano.domain.enumeration.billing.FeatureLimitType;
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

    /** Optional: derived from the name when blank ("Admin seats" → ADMIN_SEATS); fixed once created. */
    @Size(min = 2, max = 50, message = "Feature code must be between 2 and 50 characters") String code,

    @Size(max = 50, message = "Category cannot exceed 50 characters") String category,

    /** BOOLEAN (included or not) or QUANTITY (plans set a limit); defaults to BOOLEAN. */
    FeatureLimitType limitType,

    @Size(max = 50, message = "Unit cannot exceed 50 characters") String unit,

    Integer sortOrder
) {}
