package com.humano.repository.hr.projection;

/**
 * One bucket of a GROUP BY employee count — {@code key} is the grouped value (a status, a
 * work location, a department id) and {@code label} an optional display name for it.
 */
public record EmployeeCountRow(Object key, String label, long count) {}
