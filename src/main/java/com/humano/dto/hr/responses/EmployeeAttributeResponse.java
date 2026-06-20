package com.humano.dto.hr.responses;

/**
 * A single custom employee attribute (key/value) returned to clients.
 */
public record EmployeeAttributeResponse(String key, String value) {}
