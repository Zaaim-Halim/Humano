package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeLanguage record.
 */
public record CreateEmployeeLanguageRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String language,
    String reading,
    String writing,
    String speaking
) {}
