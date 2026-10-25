package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.payroll.Currency;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.dto.payroll.response.PayrollCurrencyTotals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PayrollProcessingService#totalsByCurrency} and {@link PayrollProcessingService#singleTotal}:
 * a run's totals are never added across currencies.
 */
class PayrollRunTotalsTest {

    private static Currency currency(CurrencyCode code) {
        Currency c = new Currency();
        c.setCode(code);
        return c;
    }

    private static PayrollResult result(CurrencyCode code, String gross, String deductions, String net, String employerCost) {
        PayrollResult r = new PayrollResult();
        r.setCurrency(currency(code));
        r.setGross(new BigDecimal(gross));
        r.setTotalDeductions(new BigDecimal(deductions));
        r.setNet(new BigDecimal(net));
        r.setEmployerCost(new BigDecimal(employerCost));
        return r;
    }

    @Test
    void runWithoutReportingCurrencyIsTotalledPerCurrencyAndHasNoSingleTotal() {
        PayrollRun run = new PayrollRun();
        List<PayrollResult> results = List.of(
            result(CurrencyCode.EUR, "2375.00", "533.43", "1841.57", "3138.57"),
            result(CurrencyCode.HUF, "322800", "108138", "214662", "364764"),
            result(CurrencyCode.EUR, "2000.00", "342.16", "1657.84", "2676.00")
        );

        List<PayrollCurrencyTotals> byCurrency = PayrollProcessingService.totalsByCurrency(run, results);

        assertThat(byCurrency).extracting(PayrollCurrencyTotals::currencyCode).containsExactly("EUR", "HUF");
        PayrollCurrencyTotals eur = byCurrency.get(0);
        assertThat(eur.employeeCount()).isEqualTo(2);
        assertThat(eur.totalGross()).isEqualByComparingTo("4375.00");
        assertThat(eur.totalDeductions()).isEqualByComparingTo("875.59");
        assertThat(eur.totalNet()).isEqualByComparingTo("3499.41");
        assertThat(eur.totalEmployerCost()).isEqualByComparingTo("5814.57");
        assertThat(byCurrency.get(1).totalGross()).isEqualByComparingTo("322800");
        assertThat(PayrollProcessingService.singleTotal(run, byCurrency)).isNull();
    }

    @Test
    void singleCurrencyRunKeepsItsSingleTotal() {
        PayrollRun run = new PayrollRun();
        List<PayrollCurrencyTotals> byCurrency = PayrollProcessingService.totalsByCurrency(
            run,
            List.of(result(CurrencyCode.EUR, "2375.00", "533.43", "1841.57", "3138.57"))
        );

        PayrollCurrencyTotals total = PayrollProcessingService.singleTotal(run, byCurrency);

        assertThat(total).isNotNull();
        assertThat(total.currencyCode()).isEqualTo("EUR");
        assertThat(total.totalNet()).isEqualByComparingTo("1841.57");
    }

    @Test
    void reportingCurrencyRunIsOneTotalInThatCurrency() {
        PayrollRun run = new PayrollRun();
        run.setReportingCurrency(currency(CurrencyCode.EUR));
        PayrollResult huf = result(CurrencyCode.HUF, "322800", "108138", "214662", "364764");
        huf.setReportingGross(new BigDecimal("840.00"));
        huf.setReportingTotalDeductions(new BigDecimal("281.40"));
        huf.setReportingNet(new BigDecimal("558.60"));
        huf.setReportingEmployerCost(new BigDecimal("949.20"));

        List<PayrollCurrencyTotals> byCurrency = PayrollProcessingService.totalsByCurrency(
            run,
            List.of(huf, result(CurrencyCode.EUR, "2000.00", "342.16", "1657.84", "2676.00"))
        );

        assertThat(byCurrency)
            .singleElement()
            .satisfies(t -> {
                assertThat(t.currencyCode()).isEqualTo("EUR");
                assertThat(t.employeeCount()).isEqualTo(2);
                assertThat(t.totalGross()).isEqualByComparingTo("2840.00");
                assertThat(t.totalNet()).isEqualByComparingTo("2216.44");
            });
        assertThat(PayrollProcessingService.singleTotal(run, byCurrency)).isSameAs(byCurrency.get(0));
    }

    @Test
    void runWithoutResultsHasZeroTotals() {
        PayrollRun run = new PayrollRun();

        PayrollCurrencyTotals total = PayrollProcessingService.singleTotal(run, PayrollProcessingService.totalsByCurrency(run, List.of()));

        assertThat(total.currencyCode()).isNull();
        assertThat(total.totalGross()).isEqualByComparingTo("0");
    }
}
