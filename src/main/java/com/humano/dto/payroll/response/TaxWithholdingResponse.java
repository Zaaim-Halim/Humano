package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.TaxType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for tax withholding details.
 */
public record TaxWithholdingResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    TaxType type,
    BigDecimal rate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String taxAuthority,
    String taxIdentifier,
    BigDecimal yearToDateAmount,
    boolean active
) {}
