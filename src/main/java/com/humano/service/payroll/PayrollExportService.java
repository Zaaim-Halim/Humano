package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.payroll.PayrollLine;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.repository.payroll.PayrollLineRepository;
import com.humano.repository.payroll.PayrollResultRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.EmployeeRoleHistory;
import jakarta.persistence.criteria.JoinType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A calculated payroll run as a file for accounting or the bank: CSV (the register) or Excel (the
 * register, every line, and totals). Any run past draft can be exported, including posted ones from
 * earlier periods. The file name carries the run's status, since a calculated run can still change.
 * Every export is written to the audit log.
 */
@Service
public class PayrollExportService {

    public enum Format {
        CSV("csv", "text/csv;charset=UTF-8"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }
    }

    /** An exported run: the file name to offer, its media type and the bytes. */
    public record Export(String filename, String contentType, byte[] content) {}

    private static final String AMOUNT_FORMAT = "#,##0.00";
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ENGLISH);
    private static final Map<Kind, String> KINDS = Map.of(
        Kind.EARNING,
        "Earning",
        Kind.DEDUCTION,
        "Deduction",
        Kind.EMPLOYER_CHARGE,
        "Employer charge"
    );

    private final PayrollRunRepository runRepository;
    private final PayrollResultRepository resultRepository;
    private final PayrollLineRepository lineRepository;
    private final EmployeeRoleHistory roleHistory;
    private final AuditEventService auditEventService;

    public PayrollExportService(
        PayrollRunRepository runRepository,
        PayrollResultRepository resultRepository,
        PayrollLineRepository lineRepository,
        EmployeeRoleHistory roleHistory,
        AuditEventService auditEventService
    ) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.lineRepository = lineRepository;
        this.roleHistory = roleHistory;
        this.auditEventService = auditEventService;
    }

    /** Exports a run that has been calculated, and logs the export. */
    @Transactional("tenantTransactionManager")
    public Export export(UUID runId, Format format) {
        PayrollRun run = runRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));
        if (run.getStatus() == RunStatus.DRAFT) {
            throw new BusinessRuleViolationException("A payroll run can be exported once it has been calculated");
        }
        PayrollPeriod period = run.getPeriod();
        List<PayrollResult> results = resultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> {
                root.fetch("employee");
                root.fetch("currency", JoinType.LEFT);
                return cb.equal(root.get("run").get("id"), runId);
            }
        );
        List<PayrollLine> lines = lineRepository.findAll(
            (Specification<PayrollLine>) (root, query, cb) -> {
                root.fetch("component");
                return cb.equal(root.get("result").get("run").get("id"), runId);
            }
        );
        String reportingCurrency = run.getReportingCurrency() != null ? run.getReportingCurrency().getCode().getCode() : null;
        PayrollRegister register = PayrollRegister.of(results, lines, e -> roleHistory.at(e, period.getEndDate()), reportingCurrency);

        byte[] content =
            switch (format) {
                case CSV -> csv(register);
                case XLSX -> xlsx(register, run, period);
            };
        auditEventService.record(
            "PAYROLL_EXPORTED",
            "PayrollRun",
            runId.toString(),
            Map.of("format", format.name(), "status", run.getStatus().name(), "employees", register.rows().size())
        );
        return new Export(filename(period.getCode(), run.getStatus(), format), format.contentType(), content);
    }

    static String filename(String periodCode, RunStatus status, Format format) {
        String period = periodCode != null ? periodCode.replaceAll("[^A-Za-z0-9_-]", "-") : "run";
        return "payroll-" + period + "-" + status.name().toLowerCase(Locale.ROOT) + "." + format.extension;
    }

    // ===================================================================
    // Register columns, shared by both formats
    // ===================================================================

    /** A register column: its header and how to read a row's value. */
    private record RegisterColumn(String header, java.util.function.Function<PayrollRegister.Row, Object> value) {}

    private static List<RegisterColumn> registerColumns(PayrollRegister register) {
        List<RegisterColumn> columns = new ArrayList<>();
        columns.add(new RegisterColumn("Employee number", PayrollRegister.Row::employeeNumber));
        columns.add(new RegisterColumn("Employee", PayrollRegister.Row::employeeName));
        columns.add(new RegisterColumn("Department", PayrollRegister.Row::department));
        columns.add(new RegisterColumn("Position", PayrollRegister.Row::position));
        columns.add(new RegisterColumn("Currency", PayrollRegister.Row::currency));
        register.earnings().forEach(c -> columns.add(new RegisterColumn(c.header(), r -> r.amount(c))));
        columns.add(new RegisterColumn("Gross", PayrollRegister.Row::gross));
        register.deductions().forEach(c -> columns.add(new RegisterColumn(c.header(), r -> r.amount(c))));
        columns.add(new RegisterColumn("Total deductions", PayrollRegister.Row::totalDeductions));
        columns.add(new RegisterColumn("Net", PayrollRegister.Row::net));
        register.employerCharges().forEach(c -> columns.add(new RegisterColumn(c.header(), r -> r.amount(c))));
        columns.add(new RegisterColumn("Employer cost", PayrollRegister.Row::employerCost));
        String reporting = register.reportingCurrency();
        if (reporting != null) {
            columns.add(new RegisterColumn("Exchange rate", PayrollRegister.Row::exchangeRate));
            columns.add(new RegisterColumn("Gross (" + reporting + ")", PayrollRegister.Row::reportingGross));
            columns.add(new RegisterColumn("Net (" + reporting + ")", PayrollRegister.Row::reportingNet));
            columns.add(new RegisterColumn("Employer cost (" + reporting + ")", PayrollRegister.Row::reportingEmployerCost));
        }
        return columns;
    }

    // ===================================================================
    // CSV
    // ===================================================================

    /** The register as CSV: UTF-8 with a BOM so spreadsheets read accents, plain decimals. */
    static byte[] csv(PayrollRegister register) {
        List<RegisterColumn> columns = registerColumns(register);
        StringBuilder out = new StringBuilder("﻿");
        out.append(columns.stream().map(c -> csvCell(c.header())).collect(Collectors.joining(","))).append("\r\n");
        for (PayrollRegister.Row row : register.rows()) {
            out.append(columns.stream().map(c -> csvCell(c.value().apply(row))).collect(Collectors.joining(","))).append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return amount.toPlainString();
        }
        String text = value.toString();
        // A spreadsheet runs text starting with these as a formula; names and labels are typed by users.
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return text.matches("(?s).*[\",\r\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    // ===================================================================
    // Excel
    // ===================================================================

    /** Three sheets: the register, every calculated line, and the run's totals per currency. */
    static byte[] xlsx(PayrollRegister register, PayrollRun run, PayrollPeriod period) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "Humano", "1.0");
            writeRegister(workbook.newWorksheet("Register"), register);
            writeLines(workbook.newWorksheet("Lines"), register);
            writeSummary(workbook.newWorksheet("Summary"), register, run, period);
            workbook.finish();
        } catch (IOException e) {
            throw new BusinessRuleViolationException("Failed to write the payroll export: " + e.getMessage());
        }
        return out.toByteArray();
    }

    private static void writeRegister(Worksheet sheet, PayrollRegister register) {
        List<RegisterColumn> columns = registerColumns(register);
        for (int c = 0; c < columns.size(); c++) {
            header(sheet, 0, c, columns.get(c).header());
            sheet.width(c, c < 5 ? 20 : Math.max(12, Math.min(28, columns.get(c).header().length() + 2)));
        }
        int r = 1;
        for (PayrollRegister.Row row : register.rows()) {
            for (int c = 0; c < columns.size(); c++) {
                cell(sheet, r, c, columns.get(c).value().apply(row), columns.get(c).header().equals("Exchange rate"));
            }
            r++;
        }
        sheet.freezePane(2, 1);
    }

    private static void writeLines(Worksheet sheet, PayrollRegister register) {
        String[] headers = { "Employee number", "Employee", "Currency", "Type", "Code", "Component", "Quantity", "Rate", "Amount" };
        for (int c = 0; c < headers.length; c++) {
            header(sheet, 0, c, headers[c]);
            sheet.width(c, c < 6 ? 20 : 14);
        }
        int r = 1;
        for (PayrollRegister.Line line : register.lines()) {
            sheet.value(r, 0, line.employeeNumber());
            sheet.value(r, 1, line.employeeName());
            sheet.value(r, 2, line.currency());
            sheet.value(r, 3, KINDS.get(line.kind()));
            sheet.value(r, 4, line.code());
            sheet.value(r, 5, line.label());
            cell(sheet, r, 6, line.quantity(), true);
            cell(sheet, r, 7, line.rate(), true);
            cell(sheet, r, 8, line.amount(), false);
            r++;
        }
        sheet.freezePane(0, 1);
    }

    private static void writeSummary(Worksheet sheet, PayrollRegister register, PayrollRun run, PayrollPeriod period) {
        sheet.width(0, 22);
        for (int c = 1; c < 6; c++) {
            sheet.width(c, 18);
        }
        int r = 0;
        r = summaryRow(sheet, r, "Period", period.getCode());
        r = summaryRow(sheet, r, "From", period.getStartDate());
        r = summaryRow(sheet, r, "To", period.getEndDate());
        r = summaryRow(sheet, r, "Payment date", period.getPaymentDate());
        r = summaryRow(sheet, r, "Status", statusLabel(run.getStatus()));
        if (run.getApprovedAt() != null) {
            r = summaryRow(sheet, r, "Approved", run.getApprovedAt().withOffsetSameInstant(ZoneOffset.UTC).format(EXPORTED_AT));
        }
        r = summaryRow(sheet, r, "Employees", register.rows().size());
        r = summaryRow(sheet, r, "Exported", OffsetDateTime.now(ZoneOffset.UTC).format(EXPORTED_AT));
        if (run.getStatus() == RunStatus.CALCULATED) {
            sheet.value(r++, 0, "Not approved yet: these figures can still change if the run is recalculated.");
        }

        r++;
        String[] headers = { "Currency", "Employees", "Gross", "Total deductions", "Net", "Employer cost" };
        for (int c = 0; c < headers.length; c++) {
            header(sheet, r, c, headers[c]);
        }
        r++;
        for (PayrollRegister.Total total : register.totals()) {
            r = totalRow(sheet, r, total);
        }
        if (register.reportingTotal() != null) {
            r++;
            sheet.value(r++, 0, "Converted to " + register.reportingCurrency() + " at each employee's exchange rate");
            totalRow(sheet, r, register.reportingTotal());
        }
    }

    private static int summaryRow(Worksheet sheet, int r, String label, Object value) {
        sheet.value(r, 0, label);
        sheet.style(r, 0).bold().set();
        if (value instanceof java.time.LocalDate date) {
            sheet.value(r, 1, date);
            sheet.style(r, 1).format("yyyy-mm-dd").horizontalAlignment("left").set();
        } else if (value instanceof Number number) {
            sheet.value(r, 1, number);
            sheet.style(r, 1).horizontalAlignment("left").set();
        } else if (value != null) {
            sheet.value(r, 1, value.toString());
        }
        return r + 1;
    }

    private static int totalRow(Worksheet sheet, int r, PayrollRegister.Total total) {
        sheet.value(r, 0, total.currency());
        sheet.value(r, 1, total.employees());
        cell(sheet, r, 2, total.gross(), false);
        cell(sheet, r, 3, total.totalDeductions(), false);
        cell(sheet, r, 4, total.net(), false);
        cell(sheet, r, 5, total.employerCost(), false);
        return r + 1;
    }

    private static void header(Worksheet sheet, int r, int c, String text) {
        sheet.value(r, c, text);
        sheet.style(r, c).bold().fillColor("F2F2F2").set();
    }

    /** Text as a string cell (Excel never evaluates those), amounts as numbers so they add up. */
    private static void cell(Worksheet sheet, int r, int c, Object value, boolean precise) {
        if (value == null) {
            return;
        }
        if (value instanceof BigDecimal amount) {
            sheet.value(r, c, amount);
            sheet.style(r, c).format(precise ? "General" : AMOUNT_FORMAT).set();
        } else {
            sheet.value(r, c, value.toString());
        }
    }

    private static String statusLabel(RunStatus status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case CALCULATED -> "Calculated";
            case APPROVED -> "Approved";
            case POSTED -> "Posted";
        };
    }
}
