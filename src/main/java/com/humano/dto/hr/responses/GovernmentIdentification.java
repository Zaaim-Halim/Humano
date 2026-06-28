package com.humano.dto.hr.responses;

/**
 * Nested sub-DTO grouping an employee's government identification numbers. Sensitive PII —
 * encryption-at-rest is still deferred (see docs §6). Update-only on the write side; null fields
 * are left unchanged.
 */
public record GovernmentIdentification(String nationalId, String passportNumber, String taxNumber, String socialSecurityNumber) {}
