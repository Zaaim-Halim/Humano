package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for tax calculation result.
 */
public record TaxCalculationResponse(
    UUID employeeId,
    BigDecimal taxableIncome,
    BigDecimal totalTax,
    BigDecimal effectiveTaxRate,
    String currencyCode,
    List<TaxBracketApplication> bracketBreakdown,
    List<TaxDeductionItem> deductions
) {
    public record TaxBracketApplication(
        BigDecimal bracketLower,
        BigDecimal bracketUpper,
        BigDecimal rate,
        BigDecimal taxableInBracket,
        BigDecimal taxAmount
    ) {}

    public record TaxDeductionItem(String type, String description, BigDecimal amount) {}
}
