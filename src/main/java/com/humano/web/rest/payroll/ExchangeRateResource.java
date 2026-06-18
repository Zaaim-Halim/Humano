package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.CreateExchangeRateRequest;
import com.humano.dto.payroll.response.ExchangeRateResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.payroll.ExchangeRateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Exchange rates . The point-of-use {@code convert(...)} entry point lives on
 * {@link CurrencyConversionResource} at {@code /api/payroll/conversions}.
 */
@RestController
@RequestMapping("/api/payroll/exchange-rates")
@RequirePermission(PermissionsConstants.MANAGE_EXCHANGE_RATES)
public class ExchangeRateResource {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateResource(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @PostMapping
    public ResponseEntity<ExchangeRateResponse> create(@Valid @RequestBody CreateExchangeRateRequest request) {
        ExchangeRateResponse result = exchangeRateService.createExchangeRate(request);
        return ResponseEntity.created(URI.create("/api/payroll/exchange-rates/" + result.id())).body(result);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ExchangeRateResponse>> createBulk(@Valid @RequestBody List<CreateExchangeRateRequest> requests) {
        return ResponseEntity.ok(exchangeRateService.createBulkRates(requests));
    }

    @GetMapping
    public ResponseEntity<ExchangeRateResponse> getRate(
        @RequestParam UUID fromCurrencyId,
        @RequestParam UUID toCurrencyId,
        @RequestParam LocalDate date
    ) {
        return ResponseEntity.ok(exchangeRateService.getRate(fromCurrencyId, toCurrencyId, date));
    }

    @GetMapping("/historical")
    public ResponseEntity<List<ExchangeRateResponse>> historical(
        @RequestParam UUID fromCurrencyId,
        @RequestParam UUID toCurrencyId,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate
    ) {
        return ResponseEntity.ok(exchangeRateService.getHistoricalRates(fromCurrencyId, toCurrencyId, startDate, endDate));
    }

    @GetMapping("/by-date/{date}")
    public ResponseEntity<List<ExchangeRateResponse>> byDate(@PathVariable LocalDate date) {
        return ResponseEntity.ok(exchangeRateService.getRatesForDate(date));
    }

    @GetMapping("/latest")
    public ResponseEntity<List<ExchangeRateResponse>> latest() {
        return ResponseEntity.ok(exchangeRateService.getLatestRates());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ExchangeRateResponse>> search(
        @RequestParam(required = false) UUID fromCurrencyId,
        @RequestParam(required = false) UUID toCurrencyId,
        @RequestParam(required = false) LocalDate fromDate,
        @RequestParam(required = false) LocalDate toDate,
        Pageable pageable
    ) {
        Page<ExchangeRateResponse> page = exchangeRateService.getRates(fromCurrencyId, toCurrencyId, fromDate, toDate, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        exchangeRateService.deleteRate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics(
        @RequestParam UUID fromCurrencyId,
        @RequestParam UUID toCurrencyId,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate
    ) {
        return ResponseEntity.ok(exchangeRateService.getRateStatistics(fromCurrencyId, toCurrencyId, startDate, endDate));
    }
}
