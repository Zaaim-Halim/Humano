package com.humano.dto.hr.requests;

/**
 * DTO record for partially updating a EmployeeLanguage record. Null fields are left unchanged.
 */
public record UpdateEmployeeLanguageRequest(String language, String reading, String writing, String speaking) {}
