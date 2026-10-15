package com.humano.dto.demo;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.tenant.DemoSetupStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/** A demo workspace as the console lists it. */
public record DemoWorkspaceResponse(
    UUID id,
    String name,
    String subdomain,
    CountryCode country,
    TenantStatus status,
    DemoSetupStatus setup,
    Instant expiresAt,
    String contactName,
    String contactEmail,
    UUID planId,
    String planName,
    Instant createdDate
) {}
