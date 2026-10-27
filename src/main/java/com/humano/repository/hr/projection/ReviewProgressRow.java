package com.humano.repository.hr.projection;

import java.util.UUID;

/**
 * Review counts for the employees of one department in a cycle ({@code departmentId} is
 * {@code null} for employees without a department).
 */
public record ReviewProgressRow(
    UUID departmentId,
    String departmentName,
    Long total,
    Long selfSubmitted,
    Long managerSubmitted,
    Long shared
) {}
