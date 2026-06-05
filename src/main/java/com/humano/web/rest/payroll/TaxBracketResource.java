package com.humano.web.rest.payroll;

import com.humano.domain.enumeration.payroll.TaxCode;
import com.humano.dto.payroll.request.CreateTaxBracketRequest;
import com.humano.dto.payroll.response.TaxBracketResponse;
import com.humano.dto.payroll.response.TaxCalculationResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.TaxCalculationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Tax brackets and tax calculation (P2.5).
 */
@RestController
@RequestMapping("/api/payroll/tax-brackets")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PAYROLL_ADMIN + "')")
public class TaxBracketResource {

    private final TaxCalculationService taxCalculationService;

    public TaxBracketResource(TaxCalculationService taxCalculationService) {
        this.taxCalculationService = taxCalculationService;
    }

    @PostMapping
    public ResponseEntity<TaxBracketResponse> create(@Valid @RequestBody CreateTaxBracketRequest request) {
        TaxBracketResponse result = taxCalculationService.createTaxBracket(request);
        return ResponseEntity.created(URI.create("/api/payroll/tax-brackets/" + result.id())).body(result);
    }

    public record UpdateTaxBracketRequest(BigDecimal lower, BigDecimal upper, BigDecimal rate, BigDecimal fixedPart) {}

    @PutMapping("/{id}")
    public ResponseEntity<TaxBracketResponse> update(@PathVariable UUID id, @RequestBody UpdateTaxBracketRequest body) {
        return ResponseEntity.ok(taxCalculationService.updateTaxBracket(id, body.lower(), body.upper(), body.rate(), body.fixedPart()));
    }

    @PostMapping("/{id}/expire")
    public ResponseEntity<TaxBracketResponse> expire(@PathVariable UUID id, @RequestParam LocalDate expirationDate) {
        return ResponseEntity.ok(taxCalculationService.expireTaxBracket(id, expirationDate));
    }

    @GetMapping("/active")
    public ResponseEntity<List<TaxBracketResponse>> active(
        @RequestParam UUID countryId,
        @RequestParam TaxCode taxCode,
        @RequestParam(required = false) LocalDate asOfDate
    ) {
        return ResponseEntity.ok(taxCalculationService.getActiveBrackets(countryId, taxCode, asOfDate));
    }

    public record CopyBracketsRequest(UUID countryId, TaxCode taxCode, int sourceYear, int targetYear, BigDecimal adjustmentPercentage) {}

    @PostMapping("/copy-to-year")
    public ResponseEntity<List<TaxBracketResponse>> copyToYear(@RequestBody CopyBracketsRequest body) {
        return ResponseEntity.ok(
            taxCalculationService.copyBracketsToNewYear(
                body.countryId(),
                body.taxCode(),
                body.sourceYear(),
                body.targetYear(),
                body.adjustmentPercentage()
            )
        );
    }

    public record CalculateTaxRequest(UUID countryId, TaxCode taxCode, BigDecimal taxableIncome, LocalDate asOfDate, UUID employeeId) {}

    @PostMapping("/calculate")
    public ResponseEntity<TaxCalculationResponse> calculate(@RequestBody CalculateTaxRequest body) {
        return ResponseEntity.ok(
            taxCalculationService.calculateTax(body.countryId(), body.taxCode(), body.taxableIncome(), body.asOfDate(), body.employeeId())
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestParam(required = false) LocalDate asOfDate) {
        return ResponseEntity.ok(taxCalculationService.getTaxBracketsSummary(asOfDate));
    }
}
