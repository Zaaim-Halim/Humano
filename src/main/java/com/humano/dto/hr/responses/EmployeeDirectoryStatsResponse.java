package com.humano.dto.hr.responses;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates for the employee directory header — headcount split by status, department and
 * work location, plus recent joiners. Each breakdown is one GROUP BY query.
 *
 * @param total all employees
 * @param byStatus count per {@code EmployeeStatus} name
 * @param byDepartment count per department, largest first ({@code id} null = no department)
 * @param byWorkLocation count per {@code WorkLocationType} name ({@code UNSPECIFIED} when unset)
 * @param joinedLast90Days employees whose start date is within the last 90 days
 */
public record EmployeeDirectoryStatsResponse(
    long total,
    Map<String, Long> byStatus,
    List<DepartmentCount> byDepartment,
    Map<String, Long> byWorkLocation,
    long joinedLast90Days
) {
    public record DepartmentCount(UUID id, String name, long count) {}
}
