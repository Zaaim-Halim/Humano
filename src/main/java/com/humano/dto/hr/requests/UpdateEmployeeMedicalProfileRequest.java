package com.humano.dto.hr.requests;

/**
 * DTO record for partially updating a EmployeeMedicalProfile record. Null fields are left unchanged.
 */
public record UpdateEmployeeMedicalProfileRequest(String bloodType, String allergies, String emergencyNotes) {}
