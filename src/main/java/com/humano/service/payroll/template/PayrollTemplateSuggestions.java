package com.humano.service.payroll.template;

import com.humano.domain.enumeration.CountryCode;
import com.humano.dto.payroll.response.PublicPayrollTemplateResponse;
import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.model.PayrollTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The templates a workspace in a country can start with: shipped, not a draft, and national (state or
 * municipal templates need employees' work locations, which a new workspace doesn't have). Reads the
 * catalog only — no tenant — so the public sign-up form can use it.
 */
@Service
public class PayrollTemplateSuggestions {

    private final PayrollTemplateCatalog catalog;

    public PayrollTemplateSuggestions(PayrollTemplateCatalog catalog) {
        this.catalog = catalog;
    }

    /** Templates for {@code country}, the one valid longest first. */
    public List<PublicPayrollTemplateResponse> forCountry(CountryCode country) {
        return catalog
            .templates()
            .stream()
            .filter(t -> suitable(t, country))
            .sorted(Comparator.comparing((PayrollTemplate t) -> t.validity().to()).reversed().thenComparing(PayrollTemplate::id))
            .map(PayrollTemplateSuggestions::toResponse)
            .toList();
    }

    /** The template, when it is one a workspace in {@code country} can start with. */
    public Optional<PayrollTemplate> suitable(String templateId, CountryCode country) {
        return catalog.find(templateId).filter(t -> suitable(t, country));
    }

    private static boolean suitable(PayrollTemplate t, CountryCode country) {
        return (
            country != null &&
            PayrollTemplateCatalog.adoptable(t) &&
            t.jurisdiction().subdivision() == null &&
            country.name().equals(t.jurisdiction().country())
        );
    }

    private static PublicPayrollTemplateResponse toResponse(PayrollTemplate t) {
        return new PublicPayrollTemplateResponse(
            t.id(),
            t.name(),
            t.version(),
            t.status().name(),
            t.jurisdiction().country(),
            t.currency(),
            t.validity().from(),
            t.validity().to(),
            t.payFrequencies().stream().map(Enum::name).toList(),
            t.limitations() == null ? List.of() : t.limitations()
        );
    }
}
