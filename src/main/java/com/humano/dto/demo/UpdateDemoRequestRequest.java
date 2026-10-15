package com.humano.dto.demo;

import com.humano.domain.enumeration.tenant.DemoRequestStatus;
import jakarta.validation.constraints.Size;

/** Working a demo request: its status (not {@code DEMO_CREATED}, which creating a demo sets) and notes. */
public record UpdateDemoRequestRequest(DemoRequestStatus status, @Size(max = 1000) String notes) {}
