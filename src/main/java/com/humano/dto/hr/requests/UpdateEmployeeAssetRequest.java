package com.humano.dto.hr.requests;

import java.time.LocalDate;

/**
 * DTO record for partially updating a EmployeeAsset record. Null fields are left unchanged.
 */
public record UpdateEmployeeAssetRequest(String type, String identifier, LocalDate assignedDate, LocalDate returnedDate) {}
