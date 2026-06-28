package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.CurrencyCode;

/**
 * DTO record for partially updating a EmployeeBankAccount record. Null fields are left unchanged.
 */
public record UpdateEmployeeBankAccountRequest(
    String bankName,
    String iban,
    String swift,
    String accountHolder,
    CurrencyCode currency,
    Boolean primary
) {}
