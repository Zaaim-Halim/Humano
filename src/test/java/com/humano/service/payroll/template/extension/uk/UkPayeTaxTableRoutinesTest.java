package com.humano.service.payroll.template.extension.uk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.service.payroll.template.engine.TemplateCalculationException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link UkPayeTaxTableRoutines} against HMRC's own worked examples in "Taxable Pay Tables Manual
 * Method — use from 6 April 2026" (Tables B to D), with the 2026/27 parameters of the Specification for
 * PAYE Tax Table Routines v24.0, Appendices A–C.
 */
class UkPayeTaxTableRoutinesTest {

    private final UkPayeTaxTableRoutines paye = new UkPayeTaxTableRoutines();

    // Appendix A (England & Northern Ireland) and C (Wales): R1–R4, cumulative bandwidths C1–C3, G = 2.
    private static final List<BigDecimal> RUK_RATES = decimals("0.10", "0.20", "0.40", "0.45");
    private static final List<BigDecimal> RUK_BANDS = decimals("0", "37700", "125140");
    // Appendix B (Scotland): SR1–SR6, SC1–SC5, G1 = 2.
    private static final List<BigDecimal> SCOTTISH_RATES = decimals("0.19", "0.20", "0.21", "0.42", "0.45", "0.48");
    private static final List<BigDecimal> SCOTTISH_BANDS = decimals("3967", "16956", "31092", "62430", "125140");

    private static List<BigDecimal> decimals(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private Map<String, BigDecimal> run(String code, int period, int perYear, String pay, String payToDate, String taxToDate) {
        Map<String, Object> in = new HashMap<>();
        in.put("taxCode", code);
        in.put("period", period);
        in.put("periodsPerYear", perYear);
        in.put("intervalPeriods", 1);
        in.put("pay", new BigDecimal(pay));
        in.put("payToDate", new BigDecimal(payToDate));
        in.put("taxToDate", new BigDecimal(taxToDate));
        in.put("rates", RUK_RATES);
        in.put("bands", RUK_BANDS);
        in.put("basicRateIndex", 2);
        in.put("scottishRates", SCOTTISH_RATES);
        in.put("scottishBands", SCOTTISH_BANDS);
        in.put("scottishBasicRateIndex", 2);
        in.put("welshRates", RUK_RATES);
        in.put("welshBands", RUK_BANDS);
        in.put("welshBasicRateIndex", 2);
        in.put("maxRate", new BigDecimal("0.50"));
        return paye.compute(in);
    }

    @Test
    void freePayMatchesTablesA() {
        // Examples 1, 3 and 7: code 431 at Month 4 = £1,439.68, at Week 11 = £913.66, at Week 18 = £1,495.08.
        assertThat(UkPayeTaxTableRoutines.week1Value(431, 12).multiply(BigDecimal.valueOf(4))).isEqualByComparingTo("1439.68");
        assertThat(UkPayeTaxTableRoutines.week1Value(431, 52).multiply(BigDecimal.valueOf(11))).isEqualByComparingTo("913.66");
        assertThat(UkPayeTaxTableRoutines.week1Value(431, 52).multiply(BigDecimal.valueOf(18))).isEqualByComparingTo("1495.08");
        // Para 4.3.1c: a remainder of 0 becomes 500 with one fewer multiple, so code 1000 = 1×£96.16 + (5009/52 → £96.33).
        assertThat(UkPayeTaxTableRoutines.week1Value(1000, 52)).isEqualByComparingTo("192.49");
        // Code 1257 monthly = 2×£416.67 + (2579/12 → £214.92).
        assertThat(UkPayeTaxTableRoutines.week1Value(1257, 12)).isEqualByComparingTo("1048.26");
    }

    @Test
    void example1SuffixCodeMonth4() {
        // £925 + £2,475 = £3,400 − £1,439.68 = £1,960.32 → £1,960 at 20% = £392.00 to date.
        Map<String, BigDecimal> out = run("431L", 4, 12, "925.00", "2475.00", "0");
        assertThat(out.get("taxablePayToDate")).isEqualByComparingTo("1960");
        assertThat(out.get("tax")).isEqualByComparingTo("392.00");
    }

    @Test
    void example3SuffixCodeWeek11AndTheRegulatoryLimit() {
        // £2,030 − £913.66 = £1,116.34 → £1,116 → £223.20 to date (Table B: £220.00 + £3.20).
        assertThat(run("431L", 11, 52, "203.00", "1827.00", "200.00").get("tax")).isEqualByComparingTo("23.20");
        // With nothing deducted before, £223.20 exceeds 50% of this week's £203 pay: deduct £101.50.
        Map<String, BigDecimal> limited = run("431L", 11, 52, "203.00", "1827.00", "0");
        assertThat(limited.get("tax")).isEqualByComparingTo("101.50");
        assertThat(limited.get("regulatoryLimited")).isEqualByComparingTo("1");
        assertThat(limited.get("taxToDate")).isEqualByComparingTo("101.50");
    }

    @Test
    void examples2And4CodeBr() {
        // Example 2: £3,200 to date at 20% = £640.00 due to date; £480 deducted in months 1–3 leaves £160.
        Map<String, BigDecimal> month4 = run("BR", 4, 12, "800.00", "2400.00", "480.00");
        assertThat(month4.get("taxToDate")).isEqualByComparingTo("640.00");
        assertThat(month4.get("tax")).isEqualByComparingTo("160.00");
        // Example 4: £1,500 to date = £300.00 due; £272 deducted in weeks 1–10 leaves £28.
        assertThat(run("BR", 11, 52, "140.00", "1360.00", "272.00").get("taxToDate")).isEqualByComparingTo("300.00");
    }

    @Test
    void examples5And6HigherAndAdditionalRateAtMonth4() {
        UkPayeTaxTableRoutines.Schedule rUk = new UkPayeTaxTableRoutines.Schedule(RUK_RATES, RUK_BANDS, 2);
        // Example 5: £20,300 → £2,513.46 (C1 col 3) + £3,093.20 (40% on £7,733) = £5,606.66.
        assertThat(
            UkPayeTaxTableRoutines.taxToDateOnTaxablePay(new BigDecimal("20300"), new BigDecimal("20300"), 4, 12, rUk)
        ).isEqualByComparingTo("5606.66");
        // Example 6: £49,214 → £14,172.30 (C2 col 5) + £3,375.00 (45% on £7,500) = £17,547.30.
        assertThat(
            UkPayeTaxTableRoutines.taxToDateOnTaxablePay(new BigDecimal("49214"), new BigDecimal("49214"), 4, 12, rUk)
        ).isEqualByComparingTo("17547.30");
    }

    @Test
    void example7ScottishStarterRate() {
        // S431L, Week 18: £1,612 − £1,495.08 = £116.92 → £116 at 19% = £22.04 (Table: £19.00 + £3.04).
        Map<String, BigDecimal> out = run("S431L", 18, 52, "85.00", "1527.00", "0");
        assertThat(out.get("taxablePayToDate")).isEqualByComparingTo("116");
        assertThat(out.get("tax")).isEqualByComparingTo("22.04");
    }

    @Test
    void examples10And11Welsh() {
        assertThat(run("C431L", 11, 52, "203.00", "1827.00", "200.00").get("tax")).isEqualByComparingTo("23.20");
        assertThat(run("CBR", 11, 52, "140.00", "1360.00", "272.00").get("taxToDate")).isEqualByComparingTo("300.00");
    }

    @Test
    void week1Month1BasisTreatsThePaymentInIsolation() {
        // 1257L M1, £3,000 in any month: £3,000 − £1,048.26 = £1,951.74 → £1,951 at 20% = £390.20, whatever came before.
        assertThat(run("1257L M1", 7, 12, "3000.00", "15000.00", "2000.00").get("tax")).isEqualByComparingTo("390.20");
        assertThat(run("1257LX", 7, 12, "3000.00", "0", "0").get("cumulative")).isEqualByComparingTo("0");
    }

    @Test
    void cumulativeCodesRefundOverDeductedTax() {
        // Month 6, £6,000 to date on 1257L: free pay £6,289.56 exceeds it, so £500 deducted earlier is refunded.
        assertThat(run("1257L", 6, 12, "1000.00", "5000.00", "500.00").get("tax")).isEqualByComparingTo("-500.00");
    }

    @Test
    void codesNtAndD() {
        assertThat(run("NT", 3, 12, "5000.00", "10000.00", "0").get("tax")).isEqualByComparingTo("0");
        assertThat(run("D0", 1, 12, "1000.00", "0", "0").get("tax")).isEqualByComparingTo("400.00");
        assertThat(run("SD3", 1, 12, "1000.00", "0", "0").get("tax")).isEqualByComparingTo("480.00");
    }

    @Test
    void unknownCodesAreRefused() {
        assertThatThrownBy(() -> run("SNT", 1, 12, "1000.00", "0", "0")).isInstanceOf(TemplateCalculationException.class);
        assertThatThrownBy(() -> run("1257Q", 1, 12, "1000.00", "0", "0")).isInstanceOf(TemplateCalculationException.class);
        assertThatThrownBy(() -> run("D9", 1, 12, "1000.00", "0", "0"))
            .isInstanceOf(TemplateCalculationException.class)
            .hasMessageContaining("rate band that does not exist");
    }
}
