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
    void croatiaFiguresMatchTheActsAndTheRateTable() {
        PayrollTemplate croatia = catalog.find("hr-employment").orElseThrow();
        Map<String, String> values = croatia
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));

        // Zakon o doprinosima čl. 13, 17, 14: MO I 20% (15% with MO II), MO II 5%, health 16.5%.
        assertThat(values)
            .containsEntry("pensionFirstPillarOnly", "0.20")
            .containsEntry("pensionFirstPillarWithSecond", "0.15")
            .containsEntry("pensionSecondPillar", "0.05")
            .containsEntry("healthEmployer", "0.165");
        // Zakon o porezu na dohodak čl. 14 st. 1 and čl. 24 st. 3 (NN 152/24): 600 EUR allowance, lower rate up to 5,000 a month.
        assertThat(values).containsEntry("basicPersonalAllowance", "600.00").containsEntry("lowerRateMonthlyLimit", "5000.00");
        // Naredba NN 137/24 and NN 150/25 čl. 3-4: minimum / maximum monthly base of 2025 and 2026.
        assertThat(croatia.tables().get("contributionBases").versions().get(0).rows())
            .extracting(r -> r.key() + "=" + r.values().get(0).toPlainString() + "/" + r.values().get(1).toPlainString())
            .containsExactly("2025=683.24/10788.00", "2026=757.34/11958.00");
        // Porezna uprava 2026 table: 556 local units; Zagreb 23% / 33%, Split 21.5% / 32%, Rijeka 20% / 25% (from 1.1.2026).
        Map<String, String> rates = croatia
            .tables()
            .get("municipalTaxRates")
            .versions()
            .get(0)
            .rows()
            .stream()
            .collect(Collectors.toMap(Table.Row::key, r -> r.values().get(0).toPlainString() + "/" + r.values().get(1).toPlainString()));
        assertThat(rates)
            .hasSize(556)
            .containsEntry("1333", "0.23/0.33")
            .containsEntry("4090", "0.215/0.32")
            .containsEntry("3735", "0.2/0.25");
    }

    @Test
    void romaniaFiguresMatchTheFiscalCodeAndTheOrdinances() {
        PayrollTemplate romania = catalog.find("ro-employment").orElseThrow();
        Map<String, List<String>> values = romania
            .parameters()
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, e ->
                    e.getValue().stream().map(v -> v.from() + ".." + v.to() + "=" + v.value().toPlainString()).toList()
                )
            );

        // Codul fiscal art. 138 lit. a), 156, 220^3, 78: CAS 25%, CASS 10%, CAM 2.25%, income tax 10%.
        assertThat(values.get("casRate")).containsExactly("2026-01-01..2026-12-31=0.25");
        assertThat(values.get("cassRate")).containsExactly("2026-01-01..2026-12-31=0.10");
        assertThat(values.get("camRate")).containsExactly("2026-01-01..2026-12-31=0.0225");
        assertThat(values.get("taxRate")).containsExactly("2026-01-01..2026-12-31=0.10");
        // HG 1506/2024 and HG 146/2026: minimum wage 4,050 lei, 4,325 lei from 1 July 2026.
        assertThat(values.get("minimumWage")).containsExactly("2026-01-01..2026-06-30=4050", "2026-07-01..2026-12-31=4325");
        // OUG 89/2025 art. III: 300 / 200 lei tax-free, gross cap 4,300 / 4,600 lei.
        assertThat(values.get("nonTaxableAmount")).containsExactly("2026-01-01..2026-06-30=300", "2026-07-01..2026-12-31=200");
        assertThat(values.get("nonTaxableGrossCap")).containsExactly("2026-01-01..2026-06-30=4300", "2026-07-01..2026-12-31=4600");
        // Codul fiscal art. 77 alin. (4): 20/25/30/35/45% up to the minimum wage, 0/5/10/15/25% at minimum wage + 2,000 lei.
        List<Table.Row> deduction = romania.tables().get("basicDeduction").versions().get(0).rows();
        assertThat(deduction).hasSize(41);
        assertThat(deduction)
            .filteredOn(r -> List.of("0", "5", "40").contains(r.key()))
            .extracting(r -> r.key() + "=" + r.values().stream().map(BigDecimal::toPlainString).collect(Collectors.joining("/")))
            .containsExactly("0=0.2/0.25/0.3/0.35/0.45", "5=0.175/0.225/0.275/0.325/0.425", "40=0/0.05/0.1/0.15/0.25");
    }

    @Test
    void polandFiguresMatchTheActsAndTheAnnouncements() {
        PayrollTemplate poland = catalog.find("pl-employment").orElseThrow();
        Map<String, String> values = poland
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));

        // Dz.U. 2025 poz. 1242: minimum wage 4,806 zł; M.P. 2025 poz. 1206: pension/disability limit 282,600 zł.
        assertThat(values).containsEntry("minimumWage", "4806").containsEntry("annualPensionBaseLimit", "282600");
        // Ustawa o systemie ubezpieczeń społecznych art. 16, 22: 19.52% split equally, disability 1.5% / 6.5%, sickness 2.45%.
        assertThat(values)
            .containsEntry("pensionRate", "0.0976")
            .containsEntry("disabilityEmployee", "0.015")
            .containsEntry("disabilityEmployer", "0.065")
            .containsEntry("sicknessEmployee", "0.0245");
        // Budget act 2026 art. 25-27: Labour Fund 1.0% + Solidarity Fund 1.45%, FGŚP 0.10%; health 9% (art. 79).
        assertThat(values)
            .containsEntry("labourAndSolidarityFund", "0.0245")
            .containsEntry("fgspRate", "0.001")
            .containsEntry("healthRate", "0.09");
        // PIT act art. 27, 31b, 22 ust. 2, 21 ust. 44: 12% / 32% above 120,000 zł, 300 zł monthly reduction, costs 250 / 300 zł, 85,528 zł limit.
        assertThat(values)
            .containsEntry("lowerRate", "0.12")
            .containsEntry("higherRate", "0.32")
            .containsEntry("higherRateThreshold", "120000")
            .containsEntry("monthlyReductionOneTwelfth", "300")
            .containsEntry("workCostsBasic", "250")
            .containsEntry("workCostsCommuter", "300")
            .containsEntry("exemptRevenueLimit", "85528");
        // PIT act as at 31.12.2021 (health cap, art. 83 ust. 2b): 17%, 43.76 zł monthly reduction.
        assertThat(values).containsEntry("rate2021", "0.17").containsEntry("monthlyReduction2021", "43.76");
    }

    @Test
    void hungaryFiguresMatchTheActsAndNavBooklets() {
        Map<String, String> values = catalog
            .find("hu-employment")
            .orElseThrow()
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));

        // Szja tv. 8. § (1); Tbj. 25. § (1); Szocho tv.: 15%, 18.5%, 13%.
        assertThat(values)
            .containsEntry("taxRate", "0.15")
            .containsEntry("socialSecurityRate", "0.185")
            .containsEntry("socialContributionTaxRate", "0.13");
        // Tbj. 27. § (2) / NAV booklet 49: minimum base 30% of the 322,800 Ft minimum wage.
        assertThat(values).containsEntry("minimumContributionBase", "96840");
        // Szja tv. 29/A. § (2)-(2a): family allowance 133,340 / 266,660 / 440,000 Ft, +133,340 Ft for a disabled child.
        assertThat(values)
            .containsEntry("familyOneDependant", "133340")
            .containsEntry("familyTwoDependants", "266660")
            .containsEntry("familyThreeOrMoreDependants", "440000")
            .containsEntry("familyDisabledSupplement", "133340");
        // NAV booklet 73: under-25 limit 715,765 Ft, personal 107,600 Ft; Szja tv. 29/C. §: first-married 33,335 Ft.
        assertThat(values)
            .containsEntry("youthMonthlyLimit", "715765")
            .containsEntry("personalAllowance", "107600")
            .containsEntry("firstMarriedAllowance", "33335");
    }

    @Test
    void irelandFiguresMatchTheActsAndRevenueGuidance() {
        Map<String, List<String>> values = catalog
            .find("ie-paye")
            .orElseThrow()
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(p -> p.value().toPlainString()).toList()));

        // Revenue tax rates and bands 2026: 20% / 40%, single person's band EUR 44,000.
        assertThat(values)
            .containsEntry("standardRate", List.of("0.20"))
            .containsEntry("higherRate", List.of("0.40"))
            .containsEntry("singleCutOff", List.of("44000"));
        // Finance Act 2025 s. 2 (TCA s. 531AN): USC 0.5% / 2% / 3% / 8%, bands to EUR 12,012 / 28,700 / 70,044.
        assertThat(values)
            .containsEntry("uscRate1", List.of("0.005"))
            .containsEntry("uscRate2", List.of("0.02"))
            .containsEntry("uscRate3", List.of("0.03"))
            .containsEntry("uscRate4", List.of("0.08"))
            .containsEntry("uscCutOff1", List.of("12012"))
            .containsEntry("uscCutOff2", List.of("28700"))
            .containsEntry("uscCutOff3", List.of("70044"));
        // SW (MP) Act 2024 s. 3: Class A 4.2% / 8% / 10.25% to 30 September 2026, then 4.35% / 8.15% / 10.4%.
        assertThat(values)
            .containsEntry("prsiEmployeeRate", List.of("0.042", "0.0435"))
            .containsEntry("prsiEmployerLowerRate", List.of("0.08", "0.0815"))
            .containsEntry("prsiEmployerHigherRate", List.of("0.1025", "0.104"))
            .containsEntry("nationalTrainingFundLevy", List.of("0.01"));
        // SWP Act 2015 s. 10: PRSI credit EUR 12 tapering over EUR 352.01-424 a week; SW (AE) Act 2025: employer EUR 552.
        assertThat(values)
            .containsEntry("prsiExemptUpToWeekly", List.of("352"))
            .containsEntry("prsiCreditUpToWeekly", List.of("424"))
            .containsEntry("prsiMaxCreditWeekly", List.of("12"))
            .containsEntry("prsiEmployerThresholdWeekly", List.of("552"));
    }

    @Test
    void estoniaFiguresMatchTheActsAndTheBudgetAct() {
        Map<String, String> values = catalog
            .find("ee-employment")
            .orElseThrow()
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));

        // TuMS § 4 lg 1: 22%; § 42 lg 1: 1/12 of 8 400 EUR; § 23^5: 1/12 of 9 312 EUR at pension age.
        assertThat(values)
            .containsEntry("incomeTaxRate", "0.22")
            .containsEntry("monthlyTaxFreeAmount", "700")
            .containsEntry("monthlyTaxFreeAmountPensionAge", "776");
        // VV määrus nr 78/2025: unemployment insurance 1.6% / 0.8% for 2026-2029.
        assertThat(values).containsEntry("unemploymentEmployeeRate", "0.016").containsEntry("unemploymentEmployerRate", "0.008");
        // SMS § 7 lg 1: 33%; 2026 State Budget Act § 2 lg 6: monthly base 886 EUR.
        assertThat(values).containsEntry("socialTaxRate", "0.33").containsEntry("socialTaxMonthlyBase", "886");
        // KoPS § 9: 2%, or 4% or 6% by choice.
        assertThat(values)
            .containsEntry("fundedPension2", "0.02")
            .containsEntry("fundedPension4", "0.04")
            .containsEntry("fundedPension6", "0.06");
    }

    @Test
    void spainFiguresMatchTheRegulationTheOrdenAndTheAeatAlgorithm() {
        PayrollTemplate spain = catalog.find("es-employment").orElseThrow();
        Map<String, String> values = spain
            .parameters()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).value().toPlainString()));

        // LIRPF arts. 57-58: 5 550 personal minimum; descendants 2 400 / 2 700 / 4 000 / 4 500, +2 800 under 3.
        assertThat(values)
            .containsEntry("personalMinimum", "5550")
            .containsEntry("descendant1", "2400")
            .containsEntry("descendant4", "4500")
            .containsEntry("descendantUnder3", "2800");
        // LIRPF art. 20 (RDL 4/2024): 7 302 up to 14 852, tapering to 0 at 19 747.50.
        assertThat(values).containsEntry("red20Max", "7302").containsEntry("red20Lower", "14852").containsEntry("red20Upper", "19747.50");
        // RIRPF arts. 85.3, 86: 43% cap up to 35 200; minimum rates 2% and 15%.
        assertThat(values)
            .containsEntry("capRate", "0.43")
            .containsEntry("capIncomeLimit", "35200")
            .containsEntry("minimumRateShortContract", "2.00")
            .containsEntry("minimumRateSpecial", "15.00");
        // Orden PJC/297/2026 arts. 2, 4, 16, 33: maximum base 5 101.20; 28.30%, MEI 0.90%, unemployment 7.05% / 8.30%.
        assertThat(values)
            .containsEntry("maximumBase", "5101.20")
            .containsEntry("commonEmployeeRate", "0.047")
            .containsEntry("commonEmployerRate", "0.236")
            .containsEntry("equityEmployeeRate", "0.0015")
            .containsEntry("unemploymentIndefiniteEmployeeRate", "0.0155")
            .containsEntry("unemploymentFixedTermEmployerRate", "0.067");
        // Orden art. 5: overtime 28.30% (23.60 / 4.70), force majeure 14% (12 / 2).
        assertThat(values)
            .containsEntry("overtimeEmployerRate", "0.236")
            .containsEntry("forceMajeureOvertimeEmployeeRate", "0.02")
            .containsEntry("forceMajeureOvertimeEmployerRate", "0.12");
        // Orden art. 32.1.a): IT 1.55% (1.30 / 0.25) for employees past retirement age (LGSS art. 152).
        assertThat(values).containsEntry("retiredIncapacityEmployerRate", "0.013").containsEntry("retiredIncapacityEmployeeRate", "0.0025");
        // AEAT algorithm 2026, TABLA 2 example: the scale on 24 000 is 4 225.50 + 3 800 x 30% = 5 365.50.
        Table.Row row = spain
            .tables()
            .get("withholdingScale")
            .versions()
            .get(0)
            .rows()
            .stream()
            .filter(r -> new BigDecimal("24000").compareTo(r.upper()) < 0)
            .findFirst()
            .orElseThrow();
        assertThat(row.base().add(new BigDecimal("24000").subtract(row.lower()).multiply(row.rate()))).isEqualByComparingTo("5365.50");
        // RIRPF art. 81.1 (RD 142/2024): situation 2 with two or more descendants, 19 262.
        Table.Row situation2 = spain
            .tables()
            .get("exemptLimits")
            .versions()
            .get(0)
            .rows()
            .stream()
            .filter(r -> r.key().equals("2"))
            .findFirst()
            .orElseThrow();
        assertThat(situation2.values().get(2)).isEqualByComparingTo("19262");
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
