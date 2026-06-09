package com.humano.dto.hr.responses.hierarchy;

import java.util.UUID;

/**
 * Minimal embedded reference to an employee — enough to render a person chip
 * (avatar/name/title) without round-tripping back to {@code /employees/{id}}.
 */
public record EmployeeRef(UUID id, String fullName, String jobTitle) {
    public static EmployeeRef of(UUID id, String firstName, String lastName, String jobTitle) {
        if (id == null) {
            return null;
        }
        return new EmployeeRef(id, joinFullName(firstName, lastName), jobTitle);
    }

    private static String joinFullName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty()) return last;
        if (last.isEmpty()) return first;
        return first + " " + last;
    }
}
