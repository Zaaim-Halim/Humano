package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.CurrencyCode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeBankAccount record.
 */
public record CreateEmployeeBankAccountRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String bankName,
    String iban,
    String swift,
    String accountHolder,
    CurrencyCode currency,
    Boolean primary
) {}
