package com.humano.web.rest.payroll;

import com.humano.dto.payroll.response.CurrencyConversionResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.ExchangeRateService;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Standalone currency conversion helper ({@code POST /api/payroll/conversions}).
 * Wraps {@code ExchangeRateService.convert}.
 */
@RestController
@RequestMapping("/api/payroll/conversions")
@PreAuthorize(
    "hasAnyAuthority('" +
    AuthoritiesConstants.ADMIN +
    "', '" +
    AuthoritiesConstants.PAYROLL_ADMIN +
    "', '" +
    AuthoritiesConstants.FINANCE_MANAGER +
    "')"
)
public class CurrencyConversionResource {

    private final ExchangeRateService exchangeRateService;

    public CurrencyConversionResource(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    public record ConvertRequest(
        @NotNull BigDecimal amount,
        @NotNull UUID fromCurrencyId,
        @NotNull UUID toCurrencyId,
        @NotNull LocalDate date
    ) {}

    @PostMapping
    public ResponseEntity<CurrencyConversionResponse> convert(@RequestBody ConvertRequest body) {
        return ResponseEntity.ok(exchangeRateService.convert(body.amount(), body.fromCurrencyId(), body.toCurrencyId(), body.date()));
    }
}
