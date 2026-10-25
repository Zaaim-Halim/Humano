package com.humano.service.payroll.template.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.service.payroll.template.model.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code #table.band}: the row of the band a value falls into, in an EXACT table keyed by ascending numbers. */
class TableAccessBandTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1), TO = LocalDate.of(2026, 12, 31);

    private static Table.Row row(String key, String... values) {
        return new Table.Row(
            key,
            null,
            null,
            Arrays.stream(values).map(v -> v == null ? null : new BigDecimal(v)).toList(),
            null,
            null,
            null,
            null
        );
    }

    private static TableAccess access(Table.Row... rows) {
        Table table = new Table(
            Table.Kind.EXACT,
            "bands",
            List.of("rate", "extra"),
            false,
            null,
            List.of(new Table.Version(FROM, TO, "src", List.of(rows)))
        );
        return new TableAccess("t", Map.of("bands", table), FROM, LocalDate.of(2026, 1, 31));
    }

    private final TableAccess bands = access(row("0", "0.10", null), row("1000", "0.20", "0.01"), row("5000", "0.30", "0.02"));

    @Test
    void theBandAValueFallsIntoApplies() {
        assertThat(bands.band("bands", new BigDecimal("0"), "rate")).isEqualByComparingTo("0.10");
        assertThat(bands.band("bands", new BigDecimal("999.99"), "rate")).isEqualByComparingTo("0.10");
        assertThat(bands.band("bands", new BigDecimal("1000"), "rate")).isEqualByComparingTo("0.20");
        assertThat(bands.band("bands", new BigDecimal("4999"), "extra")).isEqualByComparingTo("0.01");
        assertThat(bands.band("bands", new BigDecimal("1000000"), "rate")).isEqualByComparingTo("0.30");
    }

    @Test
    void belowTheFirstBandOrAnEmptyCellIsAnError() {
        assertThatThrownBy(() -> bands.band("bands", new BigDecimal("-1"), "rate")).hasMessageContaining("below the first band");
        assertThatThrownBy(() -> bands.band("bands", new BigDecimal("10"), "extra")).hasMessageContaining("No value for band 0");
    }

    @Test
    void keysMustBeAscendingNumbers() {
        assertThatThrownBy(() -> access(row("0", "1", "1"), row("x", "1", "1")).band("bands", BigDecimal.ONE, "rate")).hasMessageContaining(
            "not a number"
        );
        assertThatThrownBy(() -> access(row("10", "1", "1"), row("5", "1", "1")).band("bands", BigDecimal.TEN, "rate")
        ).hasMessageContaining("ascending");
    }
}
