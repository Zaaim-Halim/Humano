package com.humano.dto.me.requests;

import com.humano.domain.enumeration.hr.EventType;
import jakarta.validation.constraints.Size;

/**
 * Body for the time-clock actions (all fields optional). {@code remote} applies to clock-in
 * (working from home); {@code breakType} to starting a break (lunch, meeting or other).
 */
public record TimeClockRequest(Boolean remote, EventType breakType, @Size(max = 255) String note) {}
