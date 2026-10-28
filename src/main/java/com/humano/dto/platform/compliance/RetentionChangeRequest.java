package com.humano.dto.platform.compliance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * A new retention period. Longer (or equal) applies at once; shorter — either value — needs
 * {@code effectiveFrom}, at least 30 days ahead, and applies only to subscriptions ending from then.
 */
public record RetentionChangeRequest(@Min(30) @Max(3650) int days, @Min(1) @Max(30) int warningDays, LocalDate effectiveFrom) {}
