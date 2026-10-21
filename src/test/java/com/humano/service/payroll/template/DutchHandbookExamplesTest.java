package com.humano.service.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.catalog.PayrollTemplateValidator;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateInput;
import com.humano.service.payroll.template.engine.TemplateLine;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import com.humano.service.payroll.template.model.PayrollTemplate;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The worked examples of the Handboek Loonheffingen 2026 run through the shipped nl-employment
 * template. The VCR examples (§6.3, voorbeelden 2-4) use fictitious figures — a € 5.000 monthly
 * maximum and premiums 'stel' 10% / 7% — so the template is patched with those figures in memory.
 */
class DutchHandbookExamplesTest {

    private static final PayrollTemplateExtensionRegistry EXTENSIONS = new PayrollTemplateExtensionRegistry(List.of());
    private static final PayrollTemplateEngine ENGINE = new PayrollTemplateEngine(EXTENSIONS);
    private static final String BIRTH = "1985-04-12";

    @TempDir
    static Path dir;

    private static PayrollTemplate shipped;
    private static PayrollTemplate handbookFigures;

    @BeforeAll
    static void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode t;
        try (InputStream in = DutchHandbookExamplesTest.class.getResourceAsStream("/payroll-templates/nl/nl-employment.json")) {
            t = (ObjectNode) mapper.readTree(in);
        }
        t.put("id", "nl-handbook-figures").put("status", "DRAFT").set("testCases", mapper.createArrayNode());
        ((ObjectNode) t.at("/parameters/maxPremiumWageYear/0")).put("value", "60000");
        ((ObjectNode) t.at("/parameters/zvw/0")).put("value", "0.07");
        for (var row : (ArrayNode) t.at("/tables/maxPremiumWage/versions/0/rows")) {
            if (row.get("key").asText().equals("MONTHLY")) ((ObjectNode) row).set("values", mapper.createArrayNode().add("5000"));
        }
        Files.writeString(dir.resolve("nl-handbook-figures.json"), mapper.writeValueAsString(t));
        PayrollTemplateValidator validator = new PayrollTemplateValidator(EXTENSIONS);
        handbookFigures = new PayrollTemplateCatalog(validator, ENGINE, "file:" + dir + "/*.json")
            .find("nl-handbook-figures")
            .orElseThrow();
        shipped = new PayrollTemplateCatalog(validator, ENGINE, "classpath*:payroll-templates/nl/*.json")
            .find("nl-employment")
            .orElseThrow();
    }

    @Test
    void voorbeeld2CumulativeMaximumForEmployeeInsurances() {
        // SV-loon 4.300 / 5.500 / 5.500 -> grondslagaanwas 4.300 / 5.500 / 5.200 (Whk 'stel' 10% as the probe).
        assertThat(
            List.of(
                monthly(handbookFigures, 1, "4300", Map.of()).get("whk"),
                monthly(handbookFigures, 2, "5500", ytd("4300", 1, "4300", "4300")).get("whk"),
                monthly(handbookFigures, 3, "5500", ytd("9800", 2, "9800", "9800")).get("whk")
            )
        ).containsExactly("430.00 on 4300", "550.00 on 5500", "520.00 on 5200");
    }

    @Test
    void voorbeeld3CumulativeMaximumForTheZvw() {
        // Bijdrageloon 4.300 / 5.600 / 5.600 -> aanwas 4.300 / 5.600 / 5.100 (bijdrage 'stel' 7%).
        assertThat(
            List.of(
                monthly(handbookFigures, 1, "4300", Map.of()).get("zvw"),
                monthly(handbookFigures, 2, "5600", ytd("4300", 1, "4300", "4300")).get("zvw"),
                monthly(handbookFigures, 3, "5600", ytd("9900", 2, "9900", "9900")).get("zvw")
            )
        ).containsExactly("301.00 on 4300", "392.00 on 5600", "357.00 on 5100");
    }

    @Test
    void voorbeeld4NegativeWage() {
        // -1.700 / 5.600 / 5.600: premies (stel 10%) -170 / 560 / 560; Zvw aanwas 0 / 3.900 / 5.600, bijdrage (stel 7%) 0 / 273 / 392.
        var january = monthly(handbookFigures, 1, "-1700", Map.of());
        var february = monthly(handbookFigures, 2, "5600", ytd("-1700", 1, "-1700", "0"));
        var march = monthly(handbookFigures, 3, "5600", ytd("3900", 2, "3900", "3900"));
        assertThat(List.of(january.get("whk"), february.get("whk"), march.get("whk"))).containsExactly(
            "-170.00 on -1700",
            "560.00 on 5600",
            "560.00 on 5600"
        );
        assertThat(january).doesNotContainKey("zvw");
        assertThat(List.of(february.get("zvw"), march.get("zvw"))).containsExactly("273.00 on 3900", "392.00 on 5600");
    }

    @Test
    void paragraph18_21AowAge() {
        // Voorbeeld 1: 67 on 18 January 2026 -> from 1 January the AOW column, no employee-insurance premiums, Zvw continues.
        assertThat(monthly(shipped, 1, "3000", Map.of(), "1959-01-18", "2026-01-23")).containsOnlyKeys("wageTax", "zvw");
        // Voorbeeld 2 (applied to weekly pay): 67 on 14 May 2026 -> paid on 30 April: younger column (weektabel € 133,60 at
        // € 799,62) and premiums; paid on 1 May: AOW column born 1946 or later (€ 36,65), no premiums.
        assertThat(weekly("800", "1959-05-14", "2026-04-30"))
            .containsKeys("awf", "aof", "wko", "whk")
            .containsEntry("wageTax", "133.60 on 800");
        assertThat(weekly("800", "1959-05-14", "2026-05-01")).containsOnlyKeys("wageTax", "zvw").containsEntry("wageTax", "36.65 on 800");
    }

    private static Map<String, String> monthly(PayrollTemplate t, int month, String wage, Map<String, BigDecimal> ytd) {
        return monthly(t, month, wage, ytd, BIRTH, LocalDate.of(2026, month, 23).toString());
    }

    private static Map<String, String> monthly(
        PayrollTemplate t,
        int month,
        String wage,
        Map<String, BigDecimal> ytd,
        String birth,
        String paid
    ) {
        LocalDate start = LocalDate.of(2026, month, 1);
        return run(t, start, start.withDayOfMonth(start.lengthOfMonth()), paid, Frequency.MONTHLY, wage, birth, ytd);
    }

    private static Map<String, String> weekly(String wage, String birth, String paid) {
        LocalDate end = LocalDate.parse(paid);
        return run(shipped, end.minusDays(6), end, paid, Frequency.WEEKLY, wage, birth, Map.of());
    }

    private static Map<String, String> run(
        PayrollTemplate t,
        LocalDate start,
        LocalDate end,
        String paid,
        Frequency frequency,
        String wage,
        String birth,
        Map<String, BigDecimal> ytd
    ) {
        BigDecimal w = new BigDecimal(wage);
        TemplateInput in = new TemplateInput(
            start,
            end,
            LocalDate.parse(paid),
            frequency,
            "EUR",
            w,
            Map.of("BASIC", w),
            w,
            w,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of(),
            true,
            BigDecimal.ONE,
            LocalDate.parse(birth),
            start,
            Map.of("aofPremium", "LOW", "whkRate", "0.10"),
            Map.of("applyLoonheffingskorting", "true", "awfPremium", "LOW"),
            ytd
        );
        return ENGINE.calculate(t, in)
            .stream()
            .collect(
                Collectors.toMap(
                    TemplateLine::step,
                    l -> l.amount().toPlainString() + " on " + l.base().stripTrailingZeros().toPlainString()
                )
            );
    }

    private static Map<String, BigDecimal> ytd(String wage, int periods, String premiumBase, String zvwBase) {
        return Map.of(
            "wageTax.TAX.base",
            new BigDecimal(wage),
            "wageTax.TAX.periods",
            BigDecimal.valueOf(periods),
            "awf.EMPLOYER.base",
            new BigDecimal(premiumBase),
            "zvw.EMPLOYER.base",
            new BigDecimal(zvwBase)
        );
    }
}
