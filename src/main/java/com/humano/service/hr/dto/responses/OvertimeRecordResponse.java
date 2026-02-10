package com.humano.service.hr.dto.responses;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.enumeration.hr.OvertimeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning overtime record information.
 */
public record OvertimeRecordResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    LocalDate date,
    BigDecimal hours,
    OvertimeType type,
    OvertimeApprovalStatus approvalStatus,
    String notes,
    UUID approvedById,
    String approvedByName,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
