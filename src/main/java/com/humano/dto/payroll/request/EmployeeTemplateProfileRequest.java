package com.humano.dto.payroll.request;

import java.util.Map;

/** An employee's attributes for one payroll template (validated against the template's declared inputs). */
public record EmployeeTemplateProfileRequest(Map<String, String> attributes) {}
