package com.humano.service.payroll.template.extension.de;

import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.extension.PayrollTemplateExtension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lohnsteuer, Solidaritätszuschlag and the Maßstabsteuer for the Kirchenlohnsteuer for current wages
 * (laufender Arbeitslohn) in 2026, by the BMF's Programmablaufplan 2026 ({@link Lohnsteuer2026}, generated
 * from the official XML pseudocode).
 * <p>
 * This class only maps units: amounts come in and go out in euros, the PAP works in cents (its inputs
 * RE4, LZZFREIB, LZZHINZU; its outputs LSTLZZ, SOLZLZZ, BK). Other-payments fields (sonstige Bezüge,
 * Versorgungsbezüge, private health insurance) are left at their PAP defaults; templates that need them
 * must refuse such cases until the extension exposes them.
 *
 * <h2>Inputs</h2>
 * {@code stkl} 1-6; {@code lzz} Lohnzahlungszeitraum 1 Jahr, 2 Monat, 3 Woche, 4 Tag; {@code wage} — RE4 in
 * euros; {@code allowance}/{@code addition} — LZZFREIB/LZZHINZU in euros; {@code childAllowances} — ZKF
 * (e.g. 1.5); {@code church} — 1 if a church-tax member (R); {@code kvz} — the health fund's
 * Zusatzbeitragssatz in percent (e.g. 2.90); {@code pvs}, {@code pvz}, {@code pva} — care-insurance
 * Saxony flag, childless surcharge flag, number of child reductions; {@code alter1}/{@code ajahr} —
 * Altersentlastungsbetrag; {@code factorProcedure}/{@code factor} — Faktorverfahren (af, f).
 *
 * <h2>Outputs</h2>
 * {@code lst}, {@code solz}, {@code bk} — for the pay period, in euros.
 */
@Component
public class DeLohnsteuerPap2026 implements PayrollTemplateExtension {

    public static final String ID = "de.lohnsteuer-pap-2026";
    private static final BigDecimal CENTS = BigDecimal.valueOf(100);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> inputNames() {
        return Set.of(
            "stkl",
            "lzz",
            "wage",
            "allowance",
            "addition",
            "childAllowances",
            "church",
            "kvz",
            "pvs",
            "pvz",
            "pva",
            "alter1",
            "ajahr",
            "factorProcedure",
            "factor"
        );
    }

    @Override
    public Set<String> outputNames() {
        return Set.of("lst", "solz", "bk");
    }

    @Override
    public Map<String, BigDecimal> compute(Map<String, Object> inputs) {
        Lohnsteuer2026 pap = new Lohnsteuer2026();
        pap.STKL = integer(inputs, "stkl", 1, 6);
        pap.LZZ = integer(inputs, "lzz", 1, 4);
        pap.RE4 = cents(inputs, "wage");
        pap.LZZFREIB = cents(inputs, "allowance");
        pap.LZZHINZU = cents(inputs, "addition");
        pap.ZKF = decimal(inputs, "childAllowances");
        pap.R = integer(inputs, "church", 0, 1);
        pap.KVZ = decimal(inputs, "kvz");
        pap.PVS = integer(inputs, "pvs", 0, 1);
        pap.PVZ = integer(inputs, "pvz", 0, 1);
        pap.PVA = BigDecimal.valueOf(integer(inputs, "pva", 0, 4));
        pap.ALTER1 = integer(inputs, "alter1", 0, 1);
        pap.AJAHR = integer(inputs, "ajahr", 0, 9999);
        pap.af = integer(inputs, "factorProcedure", 0, 1);
        pap.f = decimal(inputs, "factor").doubleValue();
        // Statutory health and care insurance, pension and unemployment insurance at the general ceilings.
        pap.PKV = 0;
        pap.KRV = 0;
        pap.ALV = 0;
        pap.main();
        return Map.of("lst", euros(pap.LSTLZZ), "solz", euros(pap.SOLZLZZ), "bk", euros(pap.BK));
    }

    private static int integer(Map<String, Object> inputs, String name, int min, int max) {
        BigDecimal value = decimal(inputs, name);
        if (value.stripTrailingZeros().scale() > 0 || value.intValue() < min || value.intValue() > max) {
            throw new TemplateCalculationException(
                ID + ": " + name + " must be a whole number from " + min + " to " + max + ", not " + value
            );
        }
        return value.intValue();
    }

    private static BigDecimal decimal(Map<String, Object> inputs, String name) {
        Object value = inputs.get(name);
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Integer i) return BigDecimal.valueOf(i);
        if (value instanceof Boolean b) return b ? BigDecimal.ONE : BigDecimal.ZERO;
        if (value instanceof String text && text.matches("-?\\d+(\\.\\d+)?")) return new BigDecimal(text);
        throw new TemplateCalculationException(ID + ": input " + name + " is missing or not a number");
    }

    private static BigDecimal cents(Map<String, Object> inputs, String name) {
        BigDecimal euros = decimal(inputs, name);
        if (euros.signum() < 0) {
            throw new TemplateCalculationException(ID + ": " + name + " must not be negative");
        }
        // Whole cents: the PAP's amounts are integers in cent.
        return euros.movePointRight(2).setScale(0, RoundingMode.DOWN);
    }

    private static BigDecimal euros(BigDecimal cents) {
        return cents.divide(CENTS, 2, RoundingMode.UNNECESSARY);
    }
}
