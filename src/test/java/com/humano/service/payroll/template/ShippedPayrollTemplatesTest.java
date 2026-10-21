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

    private final PayrollTemplateExtensionRegistry extensions = new PayrollTemplateExtensionRegistry(
        List.of(
            new com.humano.service.payroll.template.extension.uk.UkPayeTaxTableRoutines(),
            new com.humano.service.payroll.template.extension.de.DeLohnsteuerPap2026()
        )
    );
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

    @Test
    void albaniaFiguresMatchTheGazetteTexts() {
        PayrollTemplate albania = catalog.find("al-employment").orElseThrow();
        Map<String, String> values = albania
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));

        // VKM 776/2025: minimum wage 50,000 ALL from 1 January 2026.
        assertThat(values).containsEntry("minimumWage", "50000");
        // VKM 77/2015 pt 3 (> 87 h/month): social 9.5% / 15%, health 1.7% / 1.7%; pt 4 (< 87 h): 9.4% / 13.9%; pt 5 (< 1 week): 0.3%.
        assertThat(values)
            .containsEntry("socialEmployee", "0.095")
            .containsEntry("socialEmployer", "0.15")
            .containsEntry("socialEmployeeUnder87", "0.094")
            .containsEntry("socialEmployerUnder87", "0.139")
            .containsEntry("socialEmployerUnderWeek", "0.003")
            .containsEntry("healthEmployee", "0.017")
            .containsEntry("healthEmployer", "0.017");
        // Law 29/2023 art. 22(1), monthly: 50,000 / 35,000 / 30,000 by monthly income band (50,000; 60,000).
        assertThat(values)
            .containsEntry("deductionUpTo50000", "50000")
            .containsEntry("deductionUpTo60000", "35000")
            .containsEntry("deductionAbove60000", "30000");
        // Law 29/2023 art. 24 + Instruction 26/2023 pt 24: 13% up to 170,000 a month (2,040,000 a year), 23% above.
        assertThat(albania.tables().get("monthlyEmploymentTax").versions().get(0).rows())
            .extracting(r -> r.lower().toPlainString() + "@" + r.rate().toPlainString())
            .containsExactly("0@0.13", "170000@0.23");
    }

    @Test
    void ukFiguresMatchHmrcPublications() {
        PayrollTemplate uk = catalog.find("gb-paye-nic").orElseThrow();
        Map<String, String> values = uk
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));
        // PAYE specification v24.0 Appendix A (2026/27): R1-R4, C1-C3, M; Appendix B (Scotland): SR1-SR6, SC1-SC5.
        assertThat(values)
            .containsEntry("rukRate2", "0.20")
            .containsEntry("rukRate3", "0.40")
            .containsEntry("rukRate4", "0.45")
            .containsEntry("rukBand2", "37700")
            .containsEntry("rukBand3", "125140")
            .containsEntry("maxRate", "0.50")
            .containsEntry("scotRate1", "0.19")
            .containsEntry("scotRate6", "0.48")
            .containsEntry("scotBand1", "3967")
            .containsEntry("scotBand4", "62430");
        // NI guidance 2026/27, 9.2: monthly ST £417, LEL £559, PT £1,048, FUST £2,083, UEL £4,189.
        assertThat(uk.tables().get("niLimits").versions().get(0).rows())
            .filteredOn(r -> r.key().equals("MONTHLY"))
            .singleElement()
            .satisfies(r ->
                assertThat(r.values()).extracting(BigDecimal::toPlainString).containsExactly("417", "559", "1048", "2083", "4189")
            );
        // Appendix 1: category A employee 8% / 8% / 2%, employer 15% throughout; C pays no employee NICs.
        assertThat(rates(uk.tables().get("niEmployeeRates"), LocalDate.of(2026, 6, 1))).containsEntry("A", new BigDecimal("0.08"));
        assertThat(rates(uk.tables().get("niEmployerRates"), LocalDate.of(2026, 6, 1))).containsEntry("A", new BigDecimal("0.15"));
        assertThat(rates(uk.tables().get("niEmployeeRates"), LocalDate.of(2026, 6, 1))).containsEntry("C", new BigDecimal("0"));
    }

    @Test
    void usFiguresMatchIrsPublications() {
        PayrollTemplate us = catalog.find("us-federal-employment").orElseThrow();
        Map<String, String> values = us
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));
        // Pub 15 (2026): 6.2% to $184,500; 1.45%; 0.9% over $200,000; FUTA 6.0% less 5.4% on $7,000.
        assertThat(values)
            .containsEntry("socialSecurityRate", "0.062")
            .containsEntry("socialSecurityWageBase", "184500")
            .containsEntry("medicareRate", "0.0145")
            .containsEntry("additionalMedicareRate", "0.009")
            .containsEntry("additionalMedicareThreshold", "200000")
            .containsEntry("futaRate", "0.060")
            .containsEntry("futaMaximumCredit", "0.054")
            .containsEntry("futaWageBase", "7000");
        // Pub 15-T (2026) Worksheet 1A lines 1g and 1k.
        assertThat(values)
            .containsEntry("standardAdjustmentMarried", "12900")
            .containsEntry("standardAdjustmentOther", "8600")
            .containsEntry("allowanceAmount", "4300");
        // Annual Percentage Method table, STANDARD Single: $0 / $7,500 10% / $19,900 $1,240 12% ... $648,100 $192,979.25 37%.
        assertThat(us.tables().get("annualWithholding").versions().get(0).rows())
            .filteredOn(r -> r.key().equals("STANDARD_SINGLE"))
            .extracting(r -> plain(r.lower()) + "@" + plain(r.rate()) + "+" + plain(r.base()))
            .containsExactly(
                "0@0+0",
                "7500@0.1+0",
                "19900@0.12+1240",
                "57900@0.22+5800",
                "113200@0.24+17966",
                "209275@0.32+41024",
                "263725@0.35+58448",
                "648100@0.37+192979.25"
            );
    }

    @Test
    void dutchFiguresMatchTheBelastingdienstPublications() {
        PayrollTemplate nl = catalog.find("nl-employment").orElseThrow();
        Map<String, String> values = nl
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> plain(e.getValue().get(0).value())));
        // Rekenvoorschriften 2026 tabel 1a, 3, 6; Handboek Loonheffingen 2026 Bijlage 1 tabellen 9, 11, 12; §18.21.
        assertThat(values)
            .containsEntry("lv", "54")
            .containsEntry("lmax", "133110")
            .containsEntry("ahkg1", "29736")
            .containsEntry("arkg4", "132920")
            .containsEntry("aowAgeYears", "67")
            .containsEntry("maxPremiumWageYear", "79409")
            .containsEntry("awfLow", "0.0274")
            .containsEntry("awfHigh", "0.0774")
            .containsEntry("aofLow", "0.0627")
            .containsEntry("aofHigh", "0.0763")
            .containsEntry("wko", "0.005")
            .containsEntry("zvw", "0.061");
        // Tabel 2, younger than AOW age: 35,75% to € 38.883, 37,56% to € 78.426 (c = € 13.900), 49,50% above (c = € 28.752).
        assertThat(nl.tables().get("tariff").versions().get(0).rows())
            .filteredOn(r -> r.key().equals("1"))
            .singleElement()
            .satisfies(r ->
                assertThat(r.values())
                    .extracting(ShippedPayrollTemplatesTest::plain)
                    .containsExactly("38883", "78426", "0.3575", "0.3756", "0.495", "13900", "28752")
            );
        // Handboek tabel 11: € 6.617,41 a month, € 1.527,09 a week.
        assertThat(nl.tables().get("maxPremiumWage").versions().get(0).rows())
            .extracting(r -> r.key() + "=" + plain(r.values().get(0)))
            .containsExactlyInAnyOrder("MONTHLY=6617.41", "WEEKLY=1527.09");
    }

    @Test
    void germanFiguresMatchTheStatutes() {
        PayrollTemplate de = catalog.find("de-employment").orElseThrow();
        Map<String, String> values = de
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> plain(e.getValue().get(0).value())));
        // SVBezGrV 2026 §§ 2, 4; § 241 SGB V; BSV 2018; § 341 SGB III; PBAV 2025 / § 55, § 58 SGB XI; § 360 SGB III.
        // Regular wages count in the year the pay period ends (§ 38a Abs. 1 Satz 2 EStG), not when they are paid.
        assertThat(de.effectiveDateBasis()).isEqualTo(PayrollTemplate.DateBasis.PERIOD);
        assertThat(values)
            .containsEntry("kvBbgMonth", "5812.5")
            .containsEntry("rvBbgMonth", "8450")
            .containsEntry("kvHalf", "0.073")
            .containsEntry("rvHalf", "0.093")
            .containsEntry("avHalf", "0.013")
            .containsEntry("pvHalf", "0.018")
            .containsEntry("pvSaxonyEmployee", "0.023")
            .containsEntry("pvSaxonyEmployer", "0.013")
            .containsEntry("pvChildless", "0.006")
            .containsEntry("pvChildReduction", "0.0025")
            .containsEntry("insolvencyLevy", "0.0015")
            .containsEntry("churchRate", "0.09")
            .containsEntry("churchRateByBw", "0.08");
        assertThat(de.steps()).anySatisfy(s -> assertThat(s.extension()).isEqualTo("de.lohnsteuer-pap-2026"));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
