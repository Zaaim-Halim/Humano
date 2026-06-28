package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeSignature record.
 */
public record CreateEmployeeSignatureRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    UUID signatureFileId,
    String certificate
) {}
