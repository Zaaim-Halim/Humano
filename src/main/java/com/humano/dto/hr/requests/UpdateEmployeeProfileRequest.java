package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.dto.hr.responses.CountryRef;
import com.humano.dto.hr.responses.EmployeePersonalDetails;
import com.humano.dto.hr.responses.GovernmentIdentification;
import com.humano.dto.hr.responses.ReferenceDataRef;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * DTO record for updating an employee profile.
 * All fields are optional to allow partial updates.
 * <p>
 * Reference-data relationships are passed as nested refs ({@code { "id": "…" }}); only the id is
 * read. When {@link #authorities} is non-null it is a full replacement of the employee's granted
 * roles (the {@code EMPLOYEE} role is always preserved); leave it null to keep roles untouched.
 */
public record UpdateEmployeeProfileRequest(
    String jobTitle,
    String phone,
    LocalDate startDate,
    LocalDate endDate,
    EmployeeStatus status,
    UUID countryId,
    UUID departmentId,
    UUID positionId,
    UUID unitId,
    UUID managerId,
    Set<String> authorities,
    // Personal / employment details (nested)
    EmployeePersonalDetails personalDetails,
    // Government identification (nested, sensitive)
    GovernmentIdentification governmentIds,
    // Reference-data relationships (nested; only id is read)
    CountryRef nationality,
    ReferenceDataRef maritalStatus,
    ReferenceDataRef employmentType,
    ReferenceDataRef grade,
    ReferenceDataRef level,
    ReferenceDataRef category,
    ReferenceDataRef terminationReason
) {}
