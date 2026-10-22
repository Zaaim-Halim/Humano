package com.humano.dto.payroll.response;

/**
 * A switched-on payroll template that covers one employee, with their profile for it (the
 * employee's page shows whether payroll can calculate them, and lets their details be filled in).
 */
public record EmployeeTemplateResponse(String templateId, String templateName, String country, TemplateEmployeeResponse employee) {}
