package com.humano.web.rest.hr;

import com.humano.domain.shared.Country;
import com.humano.repository.payroll.CountryRepository;
import com.humano.security.annotation.RequireAuthenticated;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Countries. Read-only reference data (seeded by Liquibase), for pickers such as an employee's
 * country of employment, which decides the payroll template that applies to them.
 */
@RestController
@RequestMapping("/api/hr/countries")
@RequireAuthenticated
public class CountryResource {

    private final CountryRepository countryRepository;

    public CountryResource(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    public record CountryView(UUID id, String code, String name) {
        static CountryView of(Country c) {
            return new CountryView(c.getId(), c.getCode() != null ? c.getCode().name() : null, c.getName());
        }
    }

    @GetMapping
    public ResponseEntity<List<CountryView>> list() {
        return ResponseEntity.ok(
            countryRepository.findAll().stream().map(CountryView::of).sorted(Comparator.comparing(CountryView::name)).toList()
        );
    }
}
