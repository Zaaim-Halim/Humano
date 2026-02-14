package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.enumeration.hr.OvertimeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching overtime records with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record OvertimeRecordSearchRequest(
    UUID employeeId,
    UUID approvedById,
    OvertimeType overtimeType,
    OvertimeApprovalStatus approvalStatus,
    LocalDate dateFrom,
    LocalDate dateTo,
    BigDecimal minHours,
    BigDecimal maxHours,
    String notes,
    String createdBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
