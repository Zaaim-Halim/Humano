package com.humano.dto.hr.requests;

import java.time.LocalDate;

/**
 * DTO record for partially updating a EmployeeExperience record. Null fields are left unchanged.
 */
public record UpdateEmployeeExperienceRequest(String company, String position, LocalDate startDate, LocalDate endDate) {}
