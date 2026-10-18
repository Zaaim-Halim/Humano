package com.humano.dto.billing.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * The owner console setting a customer's extra storage: how many packs (0 removes them) and, for a
 * negotiated deal, the monthly price per pack ({@code null} keeps the current one, or the offer's).
 */
public record PlatformStorageAddonRequest(@Min(0) @Max(1000) int packs, @DecimalMin("0") BigDecimal unitPrice) {}
