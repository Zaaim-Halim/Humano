package com.humano.service.payroll.template.engine;

import com.humano.service.payroll.template.model.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bound as {@code #table} while a template runs: looks values up in the template's tables, using the
 * version in force for the pay period.
 */
public final class TableAccess {

    private final String templateId;
    private final Map<String, Table> tables;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    TableAccess(String templateId, Map<String, Table> tables, LocalDate periodStart, LocalDate periodEnd) {
        this.templateId = templateId;
        this.tables = tables == null ? Map.of() : tables;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    /** The value for {@code key} in a single-column EXACT or HIERARCHICAL table. */
    public BigDecimal lookup(String tableName, Object key) {
        return lookup(tableName, key, null);
    }

    /** The value for {@code key} in {@code column} of an EXACT or HIERARCHICAL table. */
    public BigDecimal lookup(String tableName, Object key, Object column) {
        Table table = table(tableName);
        if (table.kind() == Table.Kind.BRACKETS) {
            throw new TemplateCalculationException("Table " + tableName + " holds brackets; use #table.bracketTax");
        }
        if (key == null) {
            throw new TemplateCalculationException("Lookup in " + tableName + " with a missing key");
        }
        int index = columnIndex(table, tableName, column);
        String keyText = text(key);
        List<Table.Row> rows = version(table, tableName).rows();
        List<Table.Row> matches =
            switch (table.kind()) {
                case EXACT -> rows.stream().filter(r -> keyText.equals(r.key())).toList();
                case HIERARCHICAL -> hierarchicalMatches(table, tableName, rows, keyText);
                case BRACKETS -> throw new IllegalStateException();
            };
        // Rows that leave this column empty don't apply to it.
        List<Table.Row> applicable = matches.stream().filter(r -> r.values().get(index) != null).toList();
        if (applicable.isEmpty()) {
            throw new TemplateCalculationException(
                "No value for " +
                keyText +
                (column != null ? " / " + text(column) : "") +
                " in " +
                tableName +
                (table.ambiguityHint() != null ? ". " + table.ambiguityHint() : "")
            );
        }
        List<BigDecimal> values = applicable.stream().map(r -> r.values().get(index).stripTrailingZeros()).distinct().toList();
        if (values.size() != 1) {
            String candidates = applicable
                .stream()
                .map(r -> (r.label() != null ? r.label() : r.key()) + " = " + r.values().get(index).toPlainString())
                .collect(Collectors.joining("; "));
            throw new TemplateCalculationException(
                tableName +
                " has more than one value for " +
                keyText +
                (column != null ? " / " + text(column) : "") +
                " (" +
                candidates +
                ")" +
                (table.ambiguityHint() != null ? ". " + table.ambiguityHint() : "")
            );
        }
        return applicable.get(0).values().get(index);
    }

    /** Progressive tax on {@code amount} over a BRACKETS table: each slice taxed at its bracket's rate (unrounded). */
    public BigDecimal bracketTax(String tableName, BigDecimal amount) {
        Table table = table(tableName);
        if (table.kind() != Table.Kind.BRACKETS) {
            throw new TemplateCalculationException("Table " + tableName + " is not a BRACKETS table");
        }
        if (amount == null) {
            throw new TemplateCalculationException("#table.bracketTax on " + tableName + " received a missing amount");
        }
        BigDecimal tax = BigDecimal.ZERO;
        for (Table.Row row : version(table, tableName).rows()) {
            if (amount.compareTo(row.lower()) <= 0) break;
            BigDecimal slice = amount.min(row.upper()).subtract(row.lower());
            tax = tax.add(slice.multiply(row.rate()));
        }
        return tax;
    }

    private List<Table.Row> hierarchicalMatches(Table table, String tableName, List<Table.Row> rows, String key) {
        String digits = digits(key);
        if (table.requireMostSpecific()) {
            boolean refined = rows
                .stream()
                .filter(r -> r.codes() != null)
                .flatMap(r -> Stream.concat(r.codes().include().stream(), r.codes().exclude().stream()))
                .map(TableAccess::digits)
                .anyMatch(code -> code.length() > digits.length() && code.startsWith(digits));
            if (refined) {
                throw new TemplateCalculationException(tableName + " distinguishes codes below " + key + "; use the more specific code");
            }
        }
        return rows
            .stream()
            .filter(r -> r.codes() != null)
            .filter(r -> r.codes().include().stream().anyMatch(c -> digits.startsWith(digits(c))))
            .filter(r -> r.codes().exclude() == null || r.codes().exclude().stream().noneMatch(c -> digits.startsWith(digits(c))))
            .toList();
    }

    private Table table(String name) {
        Table table = tables.get(name);
        if (table == null) {
            throw new TemplateCalculationException("Template " + templateId + " has no table " + name);
        }
        return table;
    }

    private Table.Version version(Table table, String name) {
        return table
            .versions()
            .stream()
            .filter(v -> v.covers(periodStart, periodEnd))
            .findFirst()
            .orElseThrow(() ->
                new TemplateCalculationException(
                    "Table " + name + " of " + templateId + " has no single version covering " + periodStart + "–" + periodEnd
                )
            );
    }

    private static int columnIndex(Table table, String name, Object column) {
        List<String> columns = table.columns();
        if (columns == null || columns.isEmpty()) {
            if (column != null) {
                throw new TemplateCalculationException("Table " + name + " has no columns; look it up without one");
            }
            return 0;
        }
        if (column == null) {
            throw new TemplateCalculationException("Table " + name + " needs a column (one of " + columns + ")");
        }
        int index = columns.indexOf(text(column));
        if (index < 0) {
            throw new TemplateCalculationException("Table " + name + " has no column " + text(column) + " (columns: " + columns + ")");
        }
        return index;
    }

    /** Integers, BigDecimals and strings as lookup keys: "3", 3 and 3.0 are the same key. */
    static String text(Object value) {
        if (value instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        return Objects.toString(value);
    }

    private static String digits(String code) {
        return code.replaceAll("[^0-9A-Za-z]", "");
    }
}
