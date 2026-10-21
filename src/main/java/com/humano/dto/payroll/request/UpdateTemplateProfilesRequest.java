package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sets the same attributes on several employees' profiles for one payroll template (e.g. the AWf
 * premium for everyone on a permanent contract). Attributes not named here are kept on each profile;
 * profiles are created for employees who have none.
 */
public record UpdateTemplateProfilesRequest(
    @NotEmpty(message = "Choose at least one employee") @Size(max = 1000) List<@NotNull UUID> employeeIds,
    @NotEmpty(message = "Choose at least one value to set") Map<String, String> attributes
) {}
