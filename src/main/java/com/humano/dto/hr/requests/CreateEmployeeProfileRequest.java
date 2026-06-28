package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.dto.hr.responses.CountryRef;
import com.humano.dto.hr.responses.ReferenceDataRef;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new employee profile.
 * <p>
 * Provisioning covers identity, org placement and employment classification. The reference-data
 * relationships are passed as nested refs ({@code { "id": "…" }}); only the id is read. Free-text
 * personal details and sensitive government IDs are enrichment — set them via
 * {@link UpdateEmployeeProfileRequest}.
 */
public record CreateEmployeeProfileRequest(
    String jobTitle,
    String phone,
    @NotNull(message = "Start date is required") LocalDate startDate,
    LocalDate endDate,
    EmployeeStatus status,
    UUID countryId,
    UUID departmentId,
    @NotNull(message = "Position is required") UUID positionId,
    @NotNull(message = "Organizational unit is required") UUID unitId,
    UUID managerId,
    // Reference-data relationships (nested; only id is read)
    CountryRef nationality,
    ReferenceDataRef maritalStatus,
    ReferenceDataRef employmentType,
    ReferenceDataRef grade,
    ReferenceDataRef level,
    ReferenceDataRef category,
    ReferenceDataRef terminationReason
) {}
