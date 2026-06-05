package com.humano.web.rest.payroll;

import com.humano.domain.payroll.Currency;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Currencies (P2.5). Read-only reference data &mdash; the catalog is seeded via
 * Liquibase / {@code TenantInitializationService} and not exposed for mutation here.
 */
@RestController
@RequestMapping("/api/payroll/currencies")
@PreAuthorize("isAuthenticated()")
public class CurrencyResource {

    private final CurrencyRepository currencyRepository;

    public CurrencyResource(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    public record CurrencyView(UUID id, String code, String name, String symbol) {
        static CurrencyView of(Currency c) {
            return new CurrencyView(c.getId(), c.getCode() != null ? c.getCode().name() : null, c.getName(), c.getSymbol());
        }
    }

    @GetMapping
    public ResponseEntity<List<CurrencyView>> list() {
        return ResponseEntity.ok(currencyRepository.findAll().stream().map(CurrencyView::of).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CurrencyView> get(@PathVariable UUID id) {
        Currency c = currencyRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Currency", id));
        return ResponseEntity.ok(CurrencyView.of(c));
    }
}
