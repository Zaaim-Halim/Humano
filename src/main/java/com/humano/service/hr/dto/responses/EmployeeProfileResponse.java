package com.humano.service.hr.dto.responses;

import com.humano.domain.enumeration.hr.EmployeeStatus;

import java.time.Instant;
import java.time.LocalDate;
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
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {
}
