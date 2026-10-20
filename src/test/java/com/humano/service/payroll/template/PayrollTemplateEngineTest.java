package com.humano.service.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.catalog.PayrollTemplateValidator;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateExpressions;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The template engine against synthetic fixture templates (made-up numbers) that exercise every
 * building block: floors and caps, birth-cohort rates, hierarchical lookups with conflicts, annual
 * brackets annualised per pay frequency, bands, a year-to-date cap with an April tax year, extension
 * steps and skipped steps. Each fixture carries its own hand-derived test cases; loading it through
 * the catalog runs them, so "available" means every case passed.
 */
class PayrollTemplateEngineTest {

    private final PayrollTemplateExtensionRegistry extensions = new PayrollTemplateExtensionRegistry(List.of(new ScaledTaxExtension()));
    private final PayrollTemplateValidator validator = new PayrollTemplateValidator(extensions);
    private final PayrollTemplateEngine engine = new PayrollTemplateEngine(extensions);

    @Test
    void everyFixtureValidatesAndReproducesItsTestCases() {
        PayrollTemplateCatalog catalog = new PayrollTemplateCatalog(validator, engine, "classpath*:payroll-template-fixtures/*.json");

        assertThat(catalog.rejected()).as("rejected fixtures").isEmpty();
        assertThat(catalog.templates()).extracting(t -> t.id()).containsExactly("fixture-brackets", "fixture-extension", "fixture-flat");
    }

    @Test
    void structuralMistakesAreAllReportedAndTheTemplateIsWithheld() {
        PayrollTemplateCatalog catalog = new PayrollTemplateCatalog(
            validator,
            engine,
            "classpath*:payroll-template-fixtures-invalid/broken-structure.json"
        );

        assertThat(catalog.templates()).isEmpty();
        assertThat(catalog.rejected())
            .singleElement()
            .satisfies(r ->
                assertThat(r.problems())
                    .anySatisfy(p -> assertThat(p).contains("first changelog entry must describe the current version 1.0.0"))
                    .anySatisfy(p -> assertThat(p).contains("parameter rate: dates must be contiguous"))
                    .anySatisfy(p -> assertThat(p).contains("parameter rate cites unknown source nowhere"))
                    .anySatisfy(p -> assertThat(p).contains("table pit version 2030-01-01: brackets must start at 0"))
                    .anySatisfy(p ->
                        assertThat(p).contains("table schedules version 2030-01-01 schedule A: base 150 at 1000 should be about 100")
                    )
                    .anySatisfy(p ->
                        assertThat(p).contains("table halfKeyed version 2030-01-01: either every bracket has a schedule key or none does")
                    )
                    .anySatisfy(p -> assertThat(p).contains("step literal: decimal literal 0.5"))
                    .anySatisfy(p -> assertThat(p).contains("step divides: forbidden token '/'"))
                    .anySatisfy(p -> assertThat(p).contains("step typo: #grosEarnings is not defined"))
                    .anySatisfy(p -> assertThat(p).contains("employee attribute 'qualification' is not declared"))
                    .anySatisfy(p -> assertThat(p).contains("parameter 'missing' does not exist"))
                    .anySatisfy(p -> assertThat(p).contains("#table.lookup can't be used on BRACKETS table 'pit'"))
                    .anySatisfy(p -> assertThat(p).contains("step literal is declared twice"))
                    .anySatisfy(p -> assertThat(p).contains("step taxes: id is reserved"))
                    .anySatisfy(p -> assertThat(p).contains("step contrib.employee needs exactly one of rate or amount"))
                    .anySatisfy(p -> assertThat(p).contains("a non-draft template needs test cases"))
            );
    }

    @Test
    void aTemplateWhoseWorkedExampleFailsIsWithheld() {
        PayrollTemplateCatalog catalog = new PayrollTemplateCatalog(
            validator,
            engine,
            "classpath*:payroll-template-fixtures-invalid/wrong-expectation.json"
        );

        assertThat(catalog.templates()).isEmpty();
        assertThat(catalog.rejected())
            .singleElement()
            .satisfies(r ->
                assertThat(r.problems()).containsExactly("test case 'off by one cent': expected [tax/TAX=100.01] but got [tax/TAX=100]")
            );
    }

    @Test
    void aMalformedTemplateIsRejectedWithoutStoppingTheCatalog() {
        PayrollTemplateCatalog catalog = new PayrollTemplateCatalog(
            validator,
            engine,
            "classpath*:payroll-template-fixtures-invalid/*.json"
        );

        assertThat(catalog.templates()).isEmpty();
        assertThat(catalog.rejected())
            .extracting(PayrollTemplateCatalog.Rejected::resource)
            .containsExactlyInAnyOrder("broken-structure.json", "null-field.json", "wrong-expectation.json");
        assertThat(catalog.rejected())
            .filteredOn(r -> r.resource().equals("null-field.json"))
            .singleElement()
            .satisfies(r ->
                assertThat(r.problems()).singleElement().satisfies(p -> assertThat(p).startsWith("invalid template: NullPointerException"))
            );
    }

    @Test
    void expressionsRejectInexactArithmeticAndUnsafeConstructs() {
        assertThat(TemplateExpressions.check("#a * 0.0878")).anySatisfy(p -> assertThat(p).startsWith("decimal literal 0.0878"));
        assertThat(TemplateExpressions.check("#a / #b")).anySatisfy(p -> assertThat(p).contains("forbidden token '/'"));
        assertThat(TemplateExpressions.check("#a div #b")).anySatisfy(p -> assertThat(p).contains("forbidden token 'div'"));
        assertThat(TemplateExpressions.check("#a = 3")).anySatisfy(p -> assertThat(p).contains("forbidden token '='"));
        assertThat(TemplateExpressions.check("T(java.lang.Runtime).getRuntime()")).anySatisfy(p -> assertThat(p).contains("forbidden token")
        );
        assertThat(TemplateExpressions.check("@someBean")).anySatisfy(p -> assertThat(p).contains("forbidden token '@'"));
        assertThat(TemplateExpressions.check("new java.lang.Object()")).anySatisfy(p -> assertThat(p).contains("forbidden token 'new'"));

        assertThat(TemplateExpressions.check("#divide(#a, #b, 2, 'HALF_UP') >= 1960 and #x != 'a/b 0.5'")).isEmpty();
        assertThat(TemplateExpressions.check("#a == #b or #a <= #b")).isEmpty();
    }
}
