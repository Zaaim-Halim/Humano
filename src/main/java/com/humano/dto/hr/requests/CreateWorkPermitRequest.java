package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a WorkPermit record.
 */
public record CreateWorkPermitRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String visaType,
    String permitNumber,
    LocalDate issueDate,
    LocalDate expiryDate,
    String sponsor,
    UUID documentFileId
) {}
