package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.CurrencyCode;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeBankAccount record.
 */
public record EmployeeBankAccountResponse(
    UUID id,
    UUID employeeId,
    String bankName,
    String iban,
    String swift,
    String accountHolder,
    CurrencyCode currency,
    Boolean primary,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
