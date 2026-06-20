package com.humano.dto.hr.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Replace-all payload for an employee's custom attributes
 * ({@code PUT /api/hr/employees/{employeeId}/attributes}). The supplied list is
 * the complete new set; any keys not present are removed.
 */
public record UpdateEmployeeAttributesRequest(@NotNull(message = "Attributes are required") @Valid List<EmployeeAttributeDto> attributes) {}
