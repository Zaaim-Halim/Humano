package com.humano.service.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.catalog.PayrollTemplateValidator;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import com.humano.service.payroll.template.model.PayrollTemplate;
import com.humano.service.payroll.template.model.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Every template shipped under {@code payroll-templates/} must validate and reproduce its own test
 * cases on this build's engine — the same check the catalog runs at startup, here so a failing
 * template breaks CI rather than being withheld silently in production. Also pins facts from the
 * official sources that a data edit must never change.
 */
class ShippedPayrollTemplatesTest {

    private final PayrollTemplateExtensionRegistry extensions = new PayrollTemplateExtensionRegistry(List.of());
    private final PayrollTemplateCatalog catalog = new PayrollTemplateCatalog(
        new PayrollTemplateValidator(extensions),
        new PayrollTemplateEngine(extensions)
    );

    @Test
    void everyShippedTemplateIsAvailable() {
        assertThat(catalog.rejected()).as("rejected templates").isEmpty();
        assertThat(catalog.templates()).extracting(PayrollTemplate::id).contains("bg-employment");
    }

    @Test
    void bulgariaWorkAccidentRatesChangeOnlyWhereTheNoiNoticeSays() {
        Table table = catalog.find("bg-employment").orElseThrow().tables().get("workAccidentRate");
        Map<String, BigDecimal> before = rates(table, LocalDate.of(2026, 7, 31));
        Map<String, BigDecimal> after = rates(table, LocalDate.of(2026, 8, 1));

        // NOI, "вноски за фонд ТЗПБ от 1 август 2026 г.": 7 of 87 KID-2025 codes change.
        Map<String, List<String>> changed = new HashMap<>();
        before.forEach((code, rate) -> {
            if (after.get(code).compareTo(rate) != 0) changed.put(code, List.of(rate.toPlainString(), after.get(code).toPlainString()));
        });
        assertThat(before).hasSize(87).containsOnlyKeys(after.keySet());
        assertThat(changed).isEqualTo(
            Map.of(
                "10",
                List.of("0.007", "0.009"),
                "19",
                List.of("0.009", "0.011"),
                "59",
                List.of("0.004", "0.005"),
                "71",
                List.of("0.007", "0.005"),
                "82",
                List.of("0.004", "0.005"),
                "93",
                List.of("0.005", "0.007"),
                "99",
                List.of("0.005", "0.004")
            )
        );
    }

    @Test
    void bulgariaMaximumInsurableIncomeMatchesTheBudgetAct() {
        // ZBDOO 2026 art. 9: 2111.64 EUR until 31 July, 2300 EUR from 1 August.
        assertThat(catalog.find("bg-employment").orElseThrow().parameters().get("maxInsurableIncome"))
            .extracting(v -> v.from() + ".." + v.to() + "=" + v.value().toPlainString())
            .containsExactly("2026-01-01..2026-07-31=2111.64", "2026-08-01..2026-12-31=2300.00");
    }

    private static Map<String, BigDecimal> rates(Table table, LocalDate date) {
        return table
            .versions()
            .stream()
            .filter(v -> v.covers(date, date))
            .findFirst()
            .orElseThrow()
            .rows()
            .stream()
            .collect(Collectors.toMap(Table.Row::key, r -> r.values().get(0)));
    }
}
