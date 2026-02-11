package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.TaxCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for tax bracket details.
 */
public record TaxBracketResponse(
    UUID id,
    UUID countryId,
    String countryName,
    TaxCode taxCode,
    BigDecimal lower,
    BigDecimal upper,
    BigDecimal rate,
    BigDecimal ratePercentage,
    BigDecimal fixedPart,
    LocalDate validFrom,
    LocalDate validTo,
    boolean active
) {}
