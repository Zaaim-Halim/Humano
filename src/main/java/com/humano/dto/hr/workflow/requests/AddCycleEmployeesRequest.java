package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * People to add to a running cycle — those who joined its scope after it opened.
 */
public record AddCycleEmployeesRequest(@NotEmpty(message = "Choose who to add") @Size(max = 200) Set<UUID> employeeIds) {}
