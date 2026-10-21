package com.humano.service.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.CountryCode;
import com.humano.dto.payroll.response.PublicPayrollTemplateResponse;
import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.catalog.PayrollTemplateValidator;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import com.humano.service.payroll.template.extension.de.DeLohnsteuerPap2026;
import com.humano.service.payroll.template.extension.uk.UkPayeTaxTableRoutines;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayrollTemplateSuggestionsTest {

    private final PayrollTemplateExtensionRegistry extensions = new PayrollTemplateExtensionRegistry(
        List.of(new UkPayeTaxTableRoutines(), new DeLohnsteuerPap2026())
    );
    private final PayrollTemplateSuggestions suggestions = new PayrollTemplateSuggestions(
        new PayrollTemplateCatalog(new PayrollTemplateValidator(extensions), new PayrollTemplateEngine(extensions))
    );

    @Test
    void eachShippedCountryGetsItsOwnTemplate() {
        assertThat(suggestions.forCountry(CountryCode.DE)).extracting(PublicPayrollTemplateResponse::id).containsExactly("de-employment");
        assertThat(suggestions.forCountry(CountryCode.GB)).extracting(PublicPayrollTemplateResponse::id).containsExactly("gb-paye-nic");
        assertThat(suggestions.forCountry(CountryCode.US))
            .extracting(PublicPayrollTemplateResponse::id)
            .containsExactly("us-federal-employment");
        assertThat(suggestions.forCountry(CountryCode.NL).get(0)).satisfies(t -> {
            assertThat(t.currency()).isEqualTo("EUR");
            assertThat(t.payFrequencies()).contains("MONTHLY");
            assertThat(t.limitations()).isNotEmpty();
        });
    }

    @Test
    void aCountryWithoutATemplateGetsNone() {
        assertThat(suggestions.forCountry(CountryCode.FR)).isEmpty();
        assertThat(suggestions.forCountry(null)).isEmpty();
    }

    @Test
    void aTemplateIsOnlySuitableForItsOwnCountry() {
        assertThat(suggestions.suitable("de-employment", CountryCode.DE)).isPresent();
        assertThat(suggestions.suitable("de-employment", CountryCode.AT)).isEmpty();
        assertThat(suggestions.suitable("no-such-template", CountryCode.DE)).isEmpty();
    }
}
