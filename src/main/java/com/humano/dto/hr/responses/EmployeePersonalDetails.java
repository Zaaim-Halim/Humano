package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.Gender;
import com.humano.domain.enumeration.hr.WorkLocationType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Nested sub-DTO grouping an employee's personal / employment detail fields. Used on reads
 * (fully populated) and writes (partial — null fields are left unchanged on update).
 */
public record EmployeePersonalDetails(
    String employeeNumber,
    LocalDate birthDate,
    Gender gender,
    String placeOfBirth,
    String workPhone,
    WorkLocationType workLocation,
    BigDecimal fte,
    LocalDate probationEndDate,
    LocalDate confirmationDate,
    String terminationNotes
) {}
