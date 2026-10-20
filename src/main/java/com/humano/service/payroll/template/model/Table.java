package com.humano.service.payroll.template.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An effective-dated lookup table.
 * <ul>
 *   <li>{@link Kind#EXACT} — rows keyed by {@code key}; a lookup must match exactly one row.</li>
 *   <li>{@link Kind#HIERARCHICAL} — rows cover hierarchical codes ({@code codes.include} minus
 *   {@code codes.exclude}, matched by digit prefix, so "10" ⊃ "10.1" ⊃ "10.12"). Several rows may match;
 *   those with a value in the requested column must agree, otherwise the lookup fails with
 *   {@link #ambiguityHint}. With
 *   {@link #requireMostSpecific}, a key that the table refines further (e.g. "86" when rows name
 *   "86.1") is refused.</li>
 *   <li>{@link Kind#BRACKETS} — contiguous progressive brackets starting at 0, used by
 *   {@code #table.bracketTax}.</li>
 * </ul>
 * EXACT and HIERARCHICAL rows hold one value per entry of {@link #columns} (or a single value when
 * there are no columns). A null value means the published table leaves that column empty for the row:
 * the row doesn't apply to it (e.g. an occupation-specific row that only covers some groups).
 */
public record Table(
    Kind kind,
    String description,
    List<String> columns,
    boolean requireMostSpecific,
    String ambiguityHint,
    List<Version> versions
) {
    public enum Kind {
        EXACT,
        HIERARCHICAL,
        BRACKETS,
    }

    /** The rows in force from {@code from} to {@code to} (inclusive). */
    public record Version(LocalDate from, LocalDate to, String source, List<Row> rows) {
        public boolean covers(LocalDate start, LocalDate end) {
            return !start.isBefore(from) && !end.isAfter(to);
        }
    }

    /** One row. Which fields apply depends on the table kind. */
    public record Row(
        String key,
        Codes codes,
        String label,
        List<BigDecimal> values,
        BigDecimal lower,
        BigDecimal upper,
        BigDecimal rate
    ) {}

    public record Codes(List<String> include, List<String> exclude) {}
}
