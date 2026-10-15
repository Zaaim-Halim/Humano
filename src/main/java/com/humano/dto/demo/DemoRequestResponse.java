package com.humano.dto.demo;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.tenant.DemoRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** A demo request as the console lists it, with the demo workspace set up for it, if any. */
public record DemoRequestResponse(
    UUID id,
    String fullName,
    String workEmail,
    String companyName,
    String companySize,
    CountryCode country,
    String phone,
    String message,
    DemoRequestStatus status,
    String notes,
    UUID tenantId,
    String tenantSubdomain,
    Instant createdDate,
    Instant lastModifiedDate
) {}
