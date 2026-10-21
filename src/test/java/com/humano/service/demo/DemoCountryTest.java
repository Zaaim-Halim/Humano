package com.humano.service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.catalog.PayrollTemplateValidator;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.engine.TemplateInput;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import com.humano.service.payroll.template.extension.de.DeLohnsteuerPap2026;
import com.humano.service.payroll.template.extension.uk.UkPayeTaxTableRoutines;
import com.humano.service.payroll.template.model.PayrollTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Every demo country's sample company must be one its payroll template can compute: for each
 * localised country, the shipped template runs for employees across the demo's levels, salaries and
 * ages in every month of the demo's payroll year, with the employer settings and the employee
 * attributes the demo seeder gives them — and refuses none.
 */
class DemoCountryTest {

    private static final PayrollTemplateExtensionRegistry EXTENSIONS = new PayrollTemplateExtensionRegistry(
        List.of(new UkPayeTaxTableRoutines(), new DeLohnsteuerPap2026())
    );
    private static final PayrollTemplateEngine ENGINE = new PayrollTemplateEngine(EXTENSIONS);
    private static final PayrollTemplateCatalog CATALOG = new PayrollTemplateCatalog(new PayrollTemplateValidator(EXTENSIONS), ENGINE);
    /** The demo company's levels with the lowest and highest monthly salary (US dollars) seeded at each. */
    private static final Map<String, int[]> SALARIES = Map.of(
        "INTERN",
        new int[] { 3500 },
        "JUNIOR",
        new int[] { 4200, 5400 },
        "MID",
        new int[] { 5600, 7600 },
        "SENIOR",
        new int[] { 8200, 10500 },
        "LEAD",
        new int[] { 9800, 14000 },
        "PRINCIPAL",
        new int[] { 15000, 21000 }
    );

    @ParameterizedTest
    @EnumSource(value = CountryCode.class, names = { "US", "GB", "NL", "DE", "BG", "AL" })
    void theTemplateComputesEveryDemoEmployeeInEveryMonth(CountryCode country) {
        DemoCountry local = DemoCountry.of(country).orElseThrow();
        PayrollTemplate template = CATALOG.find(local.templateId()).orElseThrow();
        assertThat(template.currency()).isEqualTo(local.currency().name());
        assertThat(PayrollTemplateCatalog.adoptable(template)).isTrue();

        Random random = new Random(1);
        List<String> refusals = new ArrayList<>();
        int calculations = 0;
        for (var level : SALARIES.entrySet()) {
            for (int usd : level.getValue()) {
                for (int age : new int[] { 23, 38, 53 }) {
                    LocalDate birth = LocalDate.of(2026 - age, 1 + random.nextInt(12), 1 + random.nextInt(28));
                    Map<String, String> attributes = local.employeeAttributes(random, level.getKey(), birth);
                    BigDecimal pay = local.salary(BigDecimal.valueOf(usd));
                    for (int month = 0; month <= 12; month++) {
                        // December 2025 to December 2026, paid five days after the month ends, as the demo calendar is.
                        LocalDate start = LocalDate.of(2025, 12, 1).plusMonths(month);
                        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                        LocalDate paid = end.plusDays(5);
                        LocalDate valuesDate = template.effectiveDateBasis() == PayrollTemplate.DateBasis.PAYMENT_DATE ? paid : start;
                        if (!template.validity().contains(valuesDate)) continue;
                        TemplateInput in = new TemplateInput(
                            start,
                            end,
                            paid,
                            Frequency.MONTHLY,
                            local.currency().name(),
                            pay,
                            Map.of("BASIC", pay),
                            pay,
                            pay,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            Map.of(),
                            !"INTERN".equals(level.getKey()),
                            "INTERN".equals(level.getKey()) ? new BigDecimal("0.50") : BigDecimal.ONE,
                            birth,
                            start,
                            true,
                            local.employerSettings(),
                            attributes,
                            Map.of()
                        );
                        try {
                            assertThat(ENGINE.calculate(template, in)).isNotEmpty();
                            calculations++;
                        } catch (TemplateCalculationException e) {
                            refusals.add(level.getKey() + " " + pay + " born " + birth + " in " + start + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
        assertThat(refusals).isEmpty();
        assertThat(calculations).isGreaterThan(100);
    }
}
