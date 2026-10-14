package com.humano.dto.billing.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One cell of the plan comparison: whether a plan includes a feature, its limit for a quantity
 * feature ({@code null} = unlimited) and an optional short note.
 */
public record PlanFeatureItem(@NotNull UUID featureId, boolean included, @Min(0) Integer limitValue, @Size(max = 100) String note) {}
