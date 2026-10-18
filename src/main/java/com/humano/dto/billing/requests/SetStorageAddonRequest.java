package com.humano.dto.billing.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** How many packs of extra storage the subscription should have (0 removes them). */
public record SetStorageAddonRequest(@Min(0) @Max(1000) int packs) {}
