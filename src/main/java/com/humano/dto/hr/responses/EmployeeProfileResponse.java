package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * DTO record for returning complete employee profile information.
 * Contains all relevant fields including relationship data and audit information.
 */
public record EmployeeProfileResponse(
    UUID id,
    String jobTitle,
    String phone,
    LocalDate startDate,
    LocalDate endDate,
    EmployeeStatus status,
    UUID countryId,
    String countryName,
    UUID departmentId,
    String departmentName,
    UUID positionId,
    String positionName,
    UUID unitId,
    String unitName,
    UUID managerId,
    String managerInfo,
    Set<String> authorities,
    // Personal / employment details (nested)
    EmployeePersonalDetails personalDetails,
    // Government identification (nested, sensitive)
    GovernmentIdentification governmentIds,
    // Reference-data relationships (nested)
    CountryRef nationality,
    ReferenceDataRef maritalStatus,
    ReferenceDataRef employmentType,
    ReferenceDataRef grade,
    ReferenceDataRef level,
    ReferenceDataRef category,
    ReferenceDataRef terminationReason,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
