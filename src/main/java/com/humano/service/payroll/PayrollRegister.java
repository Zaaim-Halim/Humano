package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.payroll.PayrollLine;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.shared.Employee;
import com.humano.service.hr.EmployeeRoleHistory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A payroll run as a table, the way it's exported: one row per employee and one column per pay
 * component. Employees don't all have the same components, so the columns are the union across
 * the run: earnings, then deductions, then employer charges, each in calculation order. A
 * component that appears twice on someone's result is summed into one cell. Amounts stay in each
 * employee's own currency, so totals are per currency.
 */
public final class PayrollRegister {

    /** A component column. Template lines share a component but carry their own label, so both identify it. */
    public record Column(Kind kind, String code, String label, String header) {}

    public record Row(
        String employeeNumber,
        String employeeName,
        String department,
        String position,
        String currency,
        BigDecimal gross,
        BigDecimal totalDeductions,
        BigDecimal net,
        BigDecimal employerCost,
        BigDecimal reportingGross,
        BigDecimal reportingNet,
        BigDecimal reportingEmployerCost,
        BigDecimal exchangeRate,
        Map<Column, BigDecimal> amounts
    ) {
        public BigDecimal amount(Column column) {
            return amounts.get(column);
        }
    }

    /** One calculated line, as it appears on the payslip. */
    public record Line(
        String employeeNumber,
        String employeeName,
        String currency,
        Kind kind,
        String code,
        String label,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount
    ) {}

    public record Total(
        String currency,
        int employees,
        BigDecimal gross,
        BigDecimal totalDeductions,
        BigDecimal net,
        BigDecimal employerCost
    ) {}

    private final List<Column> earnings;
    private final List<Column> deductions;
    private final List<Column> employerCharges;
    private final List<Row> rows;
    private final List<Line> lines;
    private final List<Total> totals;
    private final String reportingCurrency;
    private final Total reportingTotal;

    private PayrollRegister(
        List<Column> columns,
        List<Row> rows,
        List<Line> lines,
        List<Total> totals,
        String reportingCurrency,
        Total reportingTotal
    ) {
        this.earnings = columns.stream().filter(c -> c.kind() == Kind.EARNING).toList();
        this.deductions = columns.stream().filter(c -> c.kind() == Kind.DEDUCTION).toList();
        this.employerCharges = columns.stream().filter(c -> c.kind() == Kind.EMPLOYER_CHARGE).toList();
        this.rows = rows;
        this.lines = lines;
        this.totals = totals;
        this.reportingCurrency = reportingCurrency;
        this.reportingTotal = reportingTotal;
    }

    /**
     * @param lines every line of the run's results, in any order
     * @param roleOf the employee's department and position for the period
     * @param reportingCurrency the run's reporting currency code, or null when it has none
     */
    public static PayrollRegister of(
        List<PayrollResult> results,
        List<PayrollLine> lines,
        Function<Employee, EmployeeRoleHistory.Role> roleOf,
        String reportingCurrency
    ) {
        Map<UUID, List<PayrollLine>> linesByResult = lines
            .stream()
            .sorted(Comparator.comparing(l -> l.getSequence() != null ? l.getSequence() : Integer.MAX_VALUE))
            .collect(Collectors.groupingBy(l -> l.getResult().getId(), LinkedHashMap::new, Collectors.toList()));

        // The order a column first comes up in, across the run: a component calculated early for
        // anyone sorts early for everyone.
        Map<ColumnKey, Integer> firstSeen = new HashMap<>();
        for (PayrollLine line : lines) {
            int sequence = line.getSequence() != null ? line.getSequence() : Integer.MAX_VALUE;
            firstSeen.merge(ColumnKey.of(line), sequence, Math::min);
        }
        List<ColumnKey> keys = new ArrayList<>(firstSeen.keySet());
        keys.sort(
            Comparator.comparing(ColumnKey::kind)
                .thenComparing(firstSeen::get)
                .thenComparing(ColumnKey::label, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ColumnKey::code)
        );
        Map<String, Long> labelUses = keys.stream().collect(Collectors.groupingBy(ColumnKey::label, Collectors.counting()));
        Map<ColumnKey, Column> columns = new LinkedHashMap<>();
        for (ColumnKey key : keys) {
            // Two components under one label (say, two "Tax" lines) are told apart by their code.
            String header = labelUses.get(key.label()) > 1 ? key.label() + " (" + key.code() + ")" : key.label();
            columns.put(key, new Column(key.kind(), key.code(), key.label(), header));
        }

        List<Row> rows = new ArrayList<>();
        List<Line> lineRows = new ArrayList<>();
        List<PayrollResult> sorted = results.stream().sorted(Comparator.comparing(r -> sortName(r.getEmployee()))).toList();
        for (PayrollResult result : sorted) {
            Employee employee = result.getEmployee();
            String number = employeeNumber(employee);
            String name = fullName(employee);
            String currency = result.getCurrency() != null ? result.getCurrency().getCode().getCode() : null;
            Map<Column, BigDecimal> amounts = new HashMap<>();
            for (PayrollLine line : linesByResult.getOrDefault(result.getId(), List.of())) {
                Column column = columns.get(ColumnKey.of(line));
                amounts.merge(column, orZero(line.getAmount()), BigDecimal::add);
                lineRows.add(
                    new Line(
                        number,
                        name,
                        currency,
                        column.kind(),
                        column.code(),
                        column.label(),
                        line.getQuantity(),
                        line.getRate(),
                        orZero(line.getAmount())
                    )
                );
            }
            EmployeeRoleHistory.Role role = roleOf.apply(employee);
            rows.add(
                new Row(
                    number,
                    name,
                    role != null ? role.department() : null,
                    role != null ? role.position() : null,
                    currency,
                    orZero(result.getGross()),
                    orZero(result.getTotalDeductions()),
                    orZero(result.getNet()),
                    orZero(result.getEmployerCost()),
                    result.getReportingGross(),
                    result.getReportingNet(),
                    result.getReportingEmployerCost(),
                    result.getExchangeRate(),
                    amounts
                )
            );
        }

        Map<String, List<Row>> byCurrency = rows
            .stream()
            .collect(Collectors.groupingBy(r -> r.currency() != null ? r.currency() : "", LinkedHashMap::new, Collectors.toList()));
        List<Total> totals = byCurrency
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e ->
                new Total(
                    e.getKey().isEmpty() ? null : e.getKey(),
                    e.getValue().size(),
                    sum(e.getValue(), Row::gross),
                    sum(e.getValue(), Row::totalDeductions),
                    sum(e.getValue(), Row::net),
                    sum(e.getValue(), Row::employerCost)
                )
            )
            .toList();
        Total reportingTotal = null;
        if (reportingCurrency != null) {
            List<Row> converted = rows.stream().filter(r -> r.reportingNet() != null).toList();
            reportingTotal = new Total(
                reportingCurrency,
                converted.size(),
                sum(converted, Row::reportingGross),
                null,
                sum(converted, Row::reportingNet),
                sum(converted, Row::reportingEmployerCost)
            );
        }
        return new PayrollRegister(List.copyOf(columns.values()), rows, lineRows, totals, reportingCurrency, reportingTotal);
    }

    public List<Column> earnings() {
        return earnings;
    }

    public List<Column> deductions() {
        return deductions;
    }

    public List<Column> employerCharges() {
        return employerCharges;
    }

    public List<Row> rows() {
        return rows;
    }

    public List<Line> lines() {
        return lines;
    }

    /** Per currency, ordered by currency code. */
    public List<Total> totals() {
        return totals;
    }

    /** The run's reporting currency, or null when amounts aren't converted. */
    public String reportingCurrency() {
        return reportingCurrency;
    }

    /** Converted totals in the reporting currency (no deductions total), or null. */
    public Total reportingTotal() {
        return reportingTotal;
    }

    private record ColumnKey(Kind kind, String code, String label) {
        static ColumnKey of(PayrollLine line) {
            String code = line.getComponent().getCode().name();
            String label = line.displayName();
            return new ColumnKey(line.getComponent().getKind(), code, label != null ? label : code);
        }
    }

    private static String employeeNumber(Employee employee) {
        return employee.getEmployeeNumber() != null ? employee.getEmployeeNumber() : employee.getLogin();
    }

    private static String fullName(Employee employee) {
        return (employee.getFirstName() + " " + employee.getLastName()).trim();
    }

    private static String sortName(Employee employee) {
        return (employee.getLastName() + " " + employee.getFirstName()).toLowerCase();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal sum(List<Row> rows, Function<Row, BigDecimal> amount) {
        return rows.stream().map(amount).map(PayrollRegister::orZero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
