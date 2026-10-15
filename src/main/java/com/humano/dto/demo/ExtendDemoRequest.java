package com.humano.dto.demo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** More time for a demo: days from now, or from its current expiry if that is later. Reopens an expired demo. */
public record ExtendDemoRequest(@NotNull @Min(1) @Max(90) Integer days) {}
