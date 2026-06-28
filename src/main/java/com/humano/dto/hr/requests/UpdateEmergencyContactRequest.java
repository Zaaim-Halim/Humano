package com.humano.dto.hr.requests;

/**
 * DTO record for partially updating a EmergencyContact record. Null fields are left unchanged.
 */
public record UpdateEmergencyContactRequest(String name, String relationship, String phone, String email) {}
