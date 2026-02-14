package com.humano.dto.tenant.responses;

import com.humano.domain.enumeration.tenant.TenantStatus;
import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;

/**
 * DTO record for returning tenant information.
 */
public record TenantResponse(
    UUID id,
    String name,
    String domain,
    String subdomain,
    String logo,
    TimeZone timezone,
    TenantStatus status,
    String bookingPolicies,
    String hrPolicies,
    UUID subscriptionPlanId,
    String subscriptionPlanName,
    int organizationCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
