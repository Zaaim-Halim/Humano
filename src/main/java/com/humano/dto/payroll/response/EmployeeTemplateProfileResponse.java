package com.humano.dto.payroll.response;

import java.util.Map;
import java.util.UUID;

/** An employee's attributes for one payroll template. */
public record EmployeeTemplateProfileResponse(String templateId, UUID employeeId, Map<String, String> attributes) {}
