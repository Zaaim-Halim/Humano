package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.payroll.Currency;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollLine;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.domain.shared.Employee;
import com.humano.service.hr.EmployeeRoleHistory;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class PayrollExportServiceTest {

    private static final PayComponent BASIC = component(PayComponentCode.BASIC, "Base salary", Kind.EARNING);
    private static final PayComponent BONUS = component(PayComponentCode.BONUS, "Bonus", Kind.EARNING);
    private static final PayComponent TAX = component(PayComponentCode.TAX_PIT, "Income tax", Kind.DEDUCTION);
    private static final PayComponent EMPLOYER = component(PayComponentCode.EMPLOYER_CHARGE, "Social security", Kind.EMPLOYER_CHARGE);
    private static final Currency EUR = currency(CurrencyCode.EUR);
    private static final Currency USD = currency(CurrencyCode.USD);

    private final List<PayrollResult> results = new ArrayList<>();
    private final List<PayrollLine> lines = new ArrayList<>();

    @Test
    void columnsAreTheUnionAcrossEmployeesEarningsThenDeductionsThenEmployerCharges() {
        PayrollResult ava = result("Ava", "Johnson", EUR, "3500", "700", "2800", "4200");
        line(ava, EMPLOYER, null, 30, "700");
        line(ava, TAX, null, 20, "700");
        line(ava, BASIC, null, 1, "3000");
        line(ava, BONUS, null, 2, "500");
        PayrollResult ben = result("Ben", "Adams", EUR, "3000", "600", "2400", "3600");
        line(ben, BASIC, null, 1, "3000");
        line(ben, TAX, null, 20, "600");

        PayrollRegister register = register(null);

        assertThat(register.earnings()).extracting(PayrollRegister.Column::header).containsExactly("Base salary", "Bonus");
        assertThat(register.deductions()).extracting(PayrollRegister.Column::header).containsExactly("Income tax");
        assertThat(register.employerCharges()).extracting(PayrollRegister.Column::header).containsExactly("Social security");
        // Sorted by last name; Ben has no bonus, so his cell is empty rather than zero.
        assertThat(register.rows()).extracting(PayrollRegister.Row::employeeName).containsExactly("Ben Adams", "Ava Johnson");
        assertThat(register.rows().get(0).amount(register.earnings().get(1))).isNull();
        assertThat(register.lines()).hasSize(6);
    }

    @Test
    void aComponentOnTwoLinesIsSummedAndTemplateLabelsGetTheirOwnColumn() {
        PayrollResult ava = result("Ava", "Johnson", EUR, "3600", "0", "3600", "3600");
        line(ava, BASIC, null, 1, "3000");
        line(ava, BONUS, null, 2, "250");
        line(ava, BONUS, null, 3, "350");
        line(ava, TAX, "Tax", 10, "0");
        line(ava, TAX, "Tax", 11, "0");
        line(ava, BONUS, "Tax", 4, "0");

        PayrollRegister register = register(null);

        PayrollRegister.Column bonus = register.earnings().get(1);
        assertThat(bonus.header()).isEqualTo("Bonus");
        assertThat(register.rows().get(0).amount(bonus)).isEqualByComparingTo("600");
        // "Tax" is both an earning line and a deduction line, so each header names its code.
        assertThat(register.earnings()).extracting(PayrollRegister.Column::header).contains("Tax (BONUS)");
        assertThat(register.deductions()).extracting(PayrollRegister.Column::header).containsExactly("Tax (TAX_PIT)");
    }

    @Test
    void totalsArePerCurrencyAndConvertedTotalsUseTheReportingAmounts() {
        PayrollResult ava = result("Ava", "Johnson", EUR, "3000", "600", "2400", "3600");
        ava.setReportingNet(new BigDecimal("2600"));
        ava.setReportingGross(new BigDecimal("3250"));
        ava.setExchangeRate(new BigDecimal("1.0833"));
        PayrollResult ben = result("Ben", "Adams", USD, "4000", "800", "3200", "4800");
        ben.setReportingNet(new BigDecimal("3200"));
        ben.setReportingGross(new BigDecimal("4000"));
        PayrollResult cleo = result("Cleo", "Baker", EUR, "1000", "200", "800", "1200");

        PayrollRegister register = register("USD");

        assertThat(register.totals()).extracting(PayrollRegister.Total::currency).containsExactly("EUR", "USD");
        PayrollRegister.Total eur = register.totals().get(0);
        assertThat(eur.employees()).isEqualTo(2);
        assertThat(eur.net()).isEqualByComparingTo("3200");
        // Cleo wasn't converted, so the reporting total leaves her out instead of mixing currencies.
        assertThat(register.reportingTotal().employees()).isEqualTo(2);
        assertThat(register.reportingTotal().net()).isEqualByComparingTo("5800");
        assertThat(cleo.getReportingNet()).isNull();
    }

    @Test
    void csvIsTheRegisterWithPlainDecimalsAndNoFormulas() {
        PayrollResult ava = result("=HYPERLINK(\"x\")", "Johnson, Jr", EUR, "3000.50", "600", "2400.50", "3600");
        line(ava, BASIC, null, 1, "3000.50");
        line(ava, TAX, null, 20, "600");

        String csv = new String(PayrollExportService.csv(register(null)), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿");
        String[] rows = csv.substring(1).split("\r\n");
        assertThat(rows[0]).isEqualTo(
            "Employee number,Employee,Department,Position,Currency,Base salary,Gross,Income tax,Total deductions,Net,Employer cost"
        );
        assertThat(rows[1]).isEqualTo(
            "E1,\"'=HYPERLINK(\"\"x\"\") Johnson, Jr\",Engineering,Developer,EUR,3000.50,3000.50,600,600,2400.50,3600"
        );
    }

    @Test
    void xlsxHasTheRegisterLinesAndSummarySheetsWithNumericAmounts() throws IOException {
        PayrollResult ava = result("Ava", "Johnson", EUR, "3500", "700", "2800", "4200");
        line(ava, BASIC, null, 1, "3000");
        line(ava, BONUS, null, 2, "500");
        line(ava, TAX, null, 20, "700");
        PayrollPeriod period = new PayrollPeriod();
        period.setCode("2026-10");
        period.setStartDate(LocalDate.of(2026, 10, 1));
        period.setEndDate(LocalDate.of(2026, 10, 31));
        period.setPaymentDate(LocalDate.of(2026, 10, 28));
        PayrollRun run = new PayrollRun();
        run.setStatus(RunStatus.CALCULATED);

        Map<String, String> parts = unzip(PayrollExportService.xlsx(register(null), run, period));

        assertThat(parts.get("xl/workbook.xml")).contains("name=\"Register\"", "name=\"Lines\"", "name=\"Summary\"");
        // Ava's gross is a number cell, so it sums in Excel.
        assertThat(parts.get("xl/worksheets/sheet1.xml")).contains("<v>3500</v>");
        assertThat(parts.get("xl/sharedStrings.xml")).contains("Ava Johnson", "Base salary", "Not approved yet");
    }

    @Test
    void theFileNameCarriesThePeriodAndStatus() {
        assertThat(PayrollExportService.filename("2026-10", RunStatus.CALCULATED, PayrollExportService.Format.XLSX)).isEqualTo(
            "payroll-2026-10-calculated.xlsx"
        );
        assertThat(PayrollExportService.filename("2026/W 3", RunStatus.POSTED, PayrollExportService.Format.CSV)).isEqualTo(
            "payroll-2026-W-3-posted.csv"
        );
    }

    private PayrollRegister register(String reportingCurrency) {
        return PayrollRegister.of(results, lines, e -> new EmployeeRoleHistory.Role("Developer", "Engineering"), reportingCurrency);
    }

    private PayrollResult result(
        String firstName,
        String lastName,
        Currency currency,
        String gross,
        String deductions,
        String net,
        String cost
    ) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmployeeNumber("E" + (results.size() + 1));
        PayrollResult result = new PayrollResult()
            .employee(employee)
            .currency(currency)
            .gross(new BigDecimal(gross))
            .net(new BigDecimal(net));
        result.setId(UUID.randomUUID());
        result.setTotalDeductions(new BigDecimal(deductions));
        result.setEmployerCost(new BigDecimal(cost));
        results.add(result);
        return result;
    }

    private void line(PayrollResult result, PayComponent component, String label, int sequence, String amount) {
        PayrollLine line = new PayrollLine()
            .result(result)
            .component(component)
            .sequence(sequence)
            .amount(new BigDecimal(amount))
            .label(label);
        line.setId(UUID.randomUUID());
        lines.add(line);
    }

    private static PayComponent component(PayComponentCode code, String name, Kind kind) {
        PayComponent component = new PayComponent().code(code).name(name).kind(kind);
        component.setId(UUID.randomUUID());
        return component;
    }

    private static Currency currency(CurrencyCode code) {
        Currency currency = new Currency().code(code);
        currency.setId(UUID.randomUUID());
        return currency;
    }

    private static Map<String, String> unzip(byte[] xlsx) throws IOException {
        // Read through the central directory, as Excel does: the streamed entries carry their sizes after the data.
        Path file = Files.createTempFile("payroll-export", ".xlsx");
        try {
            Files.write(file, xlsx);
            Map<String, String> parts = new HashMap<>();
            try (ZipFile zip = new ZipFile(file.toFile())) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    parts.put(entry.getName(), new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return parts;
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
