package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeAsset record.
 */
public record CreateEmployeeAssetRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String type,
    String identifier,
    LocalDate assignedDate,
    LocalDate returnedDate
) {}
