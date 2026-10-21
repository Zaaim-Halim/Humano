package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An employee a payroll template covers (by country of employment), with their profile for it.
 *
 * @param attributes the profile's saved values (empty when the employee has no profile yet)
 * @param missing    the template's required attributes the profile doesn't set and that have no
 *                   default; payroll can't calculate the employee until this is empty
 */
public record TemplateEmployeeResponse(
    UUID employeeId,
    String name,
    String employeeNumber,
    String jobTitle,
    String department,
    EmployeeStatus status,
    Map<String, String> attributes,
    List<String> missing
) {}
