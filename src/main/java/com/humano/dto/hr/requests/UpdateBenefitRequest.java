package com.humano.dto.hr.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO record for updating an existing benefit.
 */
public record UpdateBenefitRequest(
    @Size(max = 255, message = "Benefit name must not exceed 255 characters") String name,

    @DecimalMin(value = "0.0", message = "Amount must be non-negative") BigDecimal amount,

    @Size(max = 500, message = "Description must not exceed 500 characters") String description
) {}
