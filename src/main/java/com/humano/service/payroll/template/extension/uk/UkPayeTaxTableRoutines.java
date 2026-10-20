package com.humano.service.payroll.template.extension.uk;

import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.extension.PayrollTemplateExtension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * HMRC's "Specification for PAYE Tax Table Routines", version 24.0 (February 2026): the PAYE income
 * tax for one payment of an employee paid weekly, two-weekly or monthly, for every tax code form —
 * suffix codes (L/M/N/T, 0T), prefix K, BR, D0..Dn and NT, with the S (Scottish) and C (Welsh) prefixes
 * and the Week 1/Month 1 basis.
 * <p>
 * Only the algorithm lives here; rates and bandwidths come from the template, so a new tax year is a
 * data change. Paragraph numbers below refer to the specification. Rounding follows it exactly:
 * intermediate values "to 4 decimal places without correction" (truncated), Cvalues rounded up to the
 * pound, taxable pay to date rounded down to the pound, tax rounded down to the penny, free pay per
 * week/month rounded up to the penny.
 *
 * <h2>Inputs</h2>
 * {@code taxCode} (e.g. "1257L", "S1257L M1", "K475", "CBR", "D0", "NT"); {@code period} — tax week or
 * month of the payment date; {@code periodsPerYear} — 52 (weekly tables, also used two-weekly) or 12;
 * {@code intervalPeriods} — weeks/months covered by one payment (1, or 2 for two-weekly), used on a
 * Week 1/Month 1 basis (para 13.5); {@code pay} — pay for the period; {@code payToDate} and
 * {@code taxToDate} — cumulative pay and tax deducted before this payment in the tax year;
 * {@code rates}/{@code bands}/{@code basicRateIndex} for England and Northern Ireland, and the
 * {@code scottish*} and {@code welsh*} equivalents (Appendix A–C: rates as fractions, cumulative
 * bandwidths, 1-based basic-rate pointer G); {@code maxRate} — the overriding regulatory limit M.
 *
 * <h2>Outputs</h2>
 * {@code tax} — deduction (positive) or refund (negative) for this payment; {@code taxToDate} — tax
 * deducted to date after it; {@code taxablePayToDate} (Tn); {@code freePay} (free pay to date, negative
 * for K-code additional pay); {@code cumulative} (1 or 0); {@code regulatoryLimited} (1 when the 50%
 * limit cut the deduction).
 */
@Component
public class UkPayeTaxTableRoutines implements PayrollTemplateExtension {

    public static final String ID = "uk.paye-routines-v24";

    private static final Pattern BASIS_SUFFIX = Pattern.compile("^(.+?)(W1|M1|X)$");
    private static final Pattern SUFFIX_CODE = Pattern.compile("^(\\d{1,6})([LMNT])$");
    private static final Pattern K_CODE = Pattern.compile("^K(\\d{1,6})$");
    private static final Pattern D_CODE = Pattern.compile("^D(\\d)$");
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal NINE = BigDecimal.valueOf(9);
    // Para 4.3.1c: 500 × £10 / 52 and / 12, rounded up to the penny.
    private static final BigDecimal CHUNK_WEEKLY = new BigDecimal("96.16");
    private static final BigDecimal CHUNK_MONTHLY = new BigDecimal("416.67");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> inputNames() {
        return Set.of(
            "taxCode",
            "period",
            "periodsPerYear",
            "intervalPeriods",
            "pay",
            "payToDate",
            "taxToDate",
            "rates",
            "bands",
            "basicRateIndex",
            "scottishRates",
            "scottishBands",
            "scottishBasicRateIndex",
            "welshRates",
            "welshBands",
            "welshBasicRateIndex",
            "maxRate"
        );
    }

    @Override
    public Set<String> outputNames() {
        return Set.of("tax", "taxToDate", "taxablePayToDate", "freePay", "cumulative", "regulatoryLimited");
    }

    /** A parsed tax code. */
    record TaxCode(String raw, boolean cumulative, char country, Kind kind, int number) {
        enum Kind {
            SUFFIX,
            K,
            BR,
            D,
            NT,
        }
    }

    /** The rate structure for one country: rates (fractions), cumulative bandwidths, basic-rate pointer G (1-based). */
    record Schedule(List<BigDecimal> rates, List<BigDecimal> cumulativeBands, int basicRateIndex) {
        Schedule {
            if (rates.size() != cumulativeBands.size() + 1) {
                throw new TemplateCalculationException(
                    "PAYE schedule needs one more rate than cumulative bandwidths (got " +
                    rates.size() +
                    " and " +
                    cumulativeBands.size() +
                    ")"
                );
            }
        }

        /** Definition 5: cumulative annual tax K(i) = Σ bandwidth(j) × rate(j), j ≤ i. */
        List<BigDecimal> cumulativeAnnualTax() {
            List<BigDecimal> k = new ArrayList<>();
            BigDecimal previousBand = BigDecimal.ZERO;
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < cumulativeBands.size(); i++) {
                sum = sum.add(cumulativeBands.get(i).subtract(previousBand).multiply(rates.get(i)));
                k.add(sum);
                previousBand = cumulativeBands.get(i);
            }
            return k;
        }

        BigDecimal rate(int oneBasedIndex, String code) {
            if (oneBasedIndex < 1 || oneBasedIndex > rates.size()) {
                throw new TemplateCalculationException("Tax code " + code + " refers to a rate band that does not exist this year");
            }
            return rates.get(oneBasedIndex - 1);
        }
    }

    @Override
    public Map<String, BigDecimal> compute(Map<String, Object> in) {
        TaxCode code = parse(string(in, "taxCode"));
        int periodsPerYear = integer(in, "periodsPerYear");
        if (periodsPerYear != 52 && periodsPerYear != 12) {
            throw new TemplateCalculationException("PAYE tables exist for weekly (52) and monthly (12) periods only");
        }
        int period = integer(in, "period");
        int interval = integer(in, "intervalPeriods");
        BigDecimal pay = decimal(in, "pay");
        BigDecimal payToDate = decimal(in, "payToDate");
        BigDecimal taxToDate = decimal(in, "taxToDate");
        BigDecimal maxRate = decimal(in, "maxRate");
        Schedule schedule =
            switch (code.country()) {
                case 'S' -> new Schedule(
                    decimals(in, "scottishRates"),
                    decimals(in, "scottishBands"),
                    integer(in, "scottishBasicRateIndex")
                );
                case 'C' -> new Schedule(decimals(in, "welshRates"), decimals(in, "welshBands"), integer(in, "welshBasicRateIndex"));
                default -> new Schedule(decimals(in, "rates"), decimals(in, "bands"), integer(in, "basicRateIndex"));
            };

        // The "week n" the tables are read for: the tax period on a cumulative basis, the number of
        // weeks/months the payment covers on a Week 1/Month 1 basis (paras 8.1, 13.5).
        int n = code.cumulative() ? period : interval;
        BigDecimal cumulativePay = code.cumulative() ? payToDate.add(pay) : pay;

        BigDecimal liability; // Ln before the regulatory limit
        BigDecimal taxablePay;
        BigDecimal freePay = BigDecimal.ZERO;
        switch (code.kind()) {
            case NT -> {
                liability = BigDecimal.ZERO; // paras 7, 11
                taxablePay = floorPound(cumulativePay);
            }
            case BR -> {
                taxablePay = floorPound(cumulativePay); // paras 5.4, 9.1
                liability = floorPenny(truncate4(taxablePay.multiply(schedule.rate(schedule.basicRateIndex(), code.raw()))));
            }
            case D -> {
                taxablePay = floorPound(cumulativePay); // paras 6.4, 10.1
                BigDecimal rate = schedule.rate(schedule.basicRateIndex() + 1 + code.number(), code.raw());
                liability = floorPenny(truncate4(taxablePay.multiply(rate)));
            }
            default -> {
                // Paras 4.3 and 8.2: free pay (suffix) or additional pay (K) for week n is n × the week-1 value.
                BigDecimal week1 = week1Value(code.number(), periodsPerYear);
                BigDecimal adjustment = week1.multiply(BigDecimal.valueOf(n));
                freePay = code.kind() == TaxCode.Kind.K ? adjustment.negate() : adjustment;
                BigDecimal unrounded = cumulativePay.subtract(freePay); // Un
                if (code.kind() == TaxCode.Kind.SUFFIX && unrounded.signum() <= 0) {
                    liability = BigDecimal.ZERO; // para 4.3.3
                    taxablePay = BigDecimal.ZERO;
                } else {
                    taxablePay = floorPound(unrounded); // para 4.4.4: Tn
                    liability = taxToDateOnTaxablePay(unrounded, taxablePay, n, periodsPerYear, schedule);
                }
            }
        }

        // Stage 4 (para 4.5): this payment's tax, cumulative or in isolation, then the regulatory limit.
        BigDecimal tax = code.cumulative() ? liability.subtract(taxToDate) : liability;
        boolean limited = false;
        if (tax.signum() > 0) {
            // Para 4.5.2 / 4.5.4: M × pay for the period, truncated to 4 dp, rounded down to 1p; nil for negative pay.
            BigDecimal limit = pay.signum() > 0 ? floorPenny(truncate4(maxRate.multiply(pay))) : BigDecimal.ZERO;
            if (tax.compareTo(limit) > 0) {
                tax = limit;
                limited = true;
            }
        } else if (!code.cumulative() && tax.signum() < 0) {
            tax = BigDecimal.ZERO; // para 4.5.4: no refund on a Week 1/Month 1 basis
        }

        Map<String, BigDecimal> out = new HashMap<>();
        out.put("tax", tax);
        out.put("taxToDate", taxToDate.add(tax));
        out.put("taxablePayToDate", taxablePay);
        out.put("freePay", freePay);
        out.put("cumulative", code.cumulative() ? BigDecimal.ONE : BigDecimal.ZERO);
        out.put("regulatoryLimited", limited ? BigDecimal.ONE : BigDecimal.ZERO);
        return out;
    }

    /**
     * Para 4.4: tax to date on taxable pay. The Income Tests compare the unrounded taxable pay Un with the
     * Cvalues (thresholds rounded up to the pound); the Tax Formula is applied to Tn.
     */
    static BigDecimal taxToDateOnTaxablePay(BigDecimal un, BigDecimal tn, int n, int periodsPerYear, Schedule schedule) {
        List<BigDecimal> bands = schedule.cumulativeBands();
        List<BigDecimal> annualTax = schedule.cumulativeAnnualTax();
        BigDecimal weeks = BigDecimal.valueOf(n);
        BigDecimal year = BigDecimal.valueOf(periodsPerYear);
        int formula = bands.size() + 1; // Tax Formula x + 1 when every Income Test fails
        for (int i = 0; i < bands.size(); i++) {
            BigDecimal threshold = truncate4(bands.get(i).multiply(weeks).divide(year, 10, RoundingMode.DOWN)); // Definition 9
            BigDecimal cvalue = threshold.setScale(0, RoundingMode.CEILING); // Definition 10
            if (un.compareTo(cvalue) <= 0) {
                formula = i + 1;
                break;
            }
        }
        // Tax Formula y: k(y−1) + (Tn − c(y−1)) × R(y), with k0 = c0 = 0.
        BigDecimal kPrevious = BigDecimal.ZERO;
        BigDecimal cPrevious = BigDecimal.ZERO;
        if (formula > 1) {
            cPrevious = truncate4(bands.get(formula - 2).multiply(weeks).divide(year, 10, RoundingMode.DOWN));
            kPrevious = truncate4(annualTax.get(formula - 2).multiply(weeks).divide(year, 10, RoundingMode.DOWN)); // Definition 11
        }
        BigDecimal rate = schedule.rates().get(formula - 1);
        return floorPenny(truncate4(kPrevious.add(tn.subtract(cPrevious).multiply(rate))));
    }

    /**
     * Para 4.3.1: free pay (suffix codes) or additional pay (K codes) for week/month 1. Code 0 is nil;
     * otherwise split the code into multiples of 500 and a remainder of 1–500; the remainder's annual value
     * (number × £10 + £9) is divided by 52 or 12 to 4 dp and rounded up to the penny, and each 500 adds
     * £96.16 a week or £416.67 a month.
     */
    static BigDecimal week1Value(int number, int periodsPerYear) {
        if (number == 0) {
            return BigDecimal.ZERO;
        }
        int quotient = (number - 1) / 500;
        int remainder = ((number - 1) % 500) + 1;
        BigDecimal annual = BigDecimal.valueOf(remainder).multiply(TEN).add(NINE);
        BigDecimal remainderValue = truncate4(annual.divide(BigDecimal.valueOf(periodsPerYear), 10, RoundingMode.DOWN)).setScale(
            2,
            RoundingMode.CEILING
        );
        BigDecimal chunk = periodsPerYear == 52 ? CHUNK_WEEKLY : CHUNK_MONTHLY;
        return remainderValue.add(chunk.multiply(BigDecimal.valueOf(quotient)));
    }

    /**
     * Parses a tax code. The basis suffix (W1, M1 or X, with or without a space) marks a Week 1/Month 1
     * code; the S or C prefix selects the Scottish or Welsh rates (para 4.3.4). NT and the emergency code
     * carry no prefix.
     */
    static TaxCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TemplateCalculationException("The employee's tax code is missing");
        }
        String code = raw.toUpperCase().replaceAll("\\s+", "");
        boolean cumulative = true;
        Matcher basis = BASIS_SUFFIX.matcher(code);
        if (basis.matches() && isCode(basis.group(1))) {
            code = basis.group(1);
            cumulative = false;
        }
        char country = 'E';
        if (!code.equals("NT") && (code.startsWith("S") || code.startsWith("C"))) {
            country = code.charAt(0);
            code = code.substring(1);
        }
        TaxCode parsed = codeOf(raw, cumulative, country, code);
        if (parsed == null) {
            throw new TemplateCalculationException("'" + raw + "' is not a PAYE tax code this template understands");
        }
        return parsed;
    }

    private static boolean isCode(String code) {
        String body = (code.startsWith("S") || code.startsWith("C")) && !code.equals("NT") ? code.substring(1) : code;
        return codeOf(code, true, 'E', body) != null;
    }

    private static TaxCode codeOf(String raw, boolean cumulative, char country, String body) {
        if (body.equals("NT")) return country == 'E' ? new TaxCode(raw, cumulative, country, TaxCode.Kind.NT, 0) : null;
        if (body.equals("BR")) return new TaxCode(raw, cumulative, country, TaxCode.Kind.BR, 0);
        Matcher d = D_CODE.matcher(body);
        if (d.matches()) return new TaxCode(raw, cumulative, country, TaxCode.Kind.D, Integer.parseInt(d.group(1)));
        Matcher k = K_CODE.matcher(body);
        if (k.matches() && Integer.parseInt(k.group(1)) > 0) {
            return new TaxCode(raw, cumulative, country, TaxCode.Kind.K, Integer.parseInt(k.group(1)));
        }
        Matcher suffix = SUFFIX_CODE.matcher(body);
        if (suffix.matches()) return new TaxCode(raw, cumulative, country, TaxCode.Kind.SUFFIX, Integer.parseInt(suffix.group(1)));
        return null;
    }

    /** "To 4 decimal places of a pound without applying any correction to the final place." */
    static BigDecimal truncate4(BigDecimal value) {
        return value.setScale(4, RoundingMode.DOWN);
    }

    static BigDecimal floorPenny(BigDecimal value) {
        return value.setScale(2, RoundingMode.FLOOR);
    }

    static BigDecimal floorPound(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR);
    }

    private static String string(Map<String, Object> in, String name) {
        Object value = in.get(name);
        return value == null ? null : value.toString();
    }

    private static int integer(Map<String, Object> in, String name) {
        return decimal(in, name).intValueExact();
    }

    private static BigDecimal decimal(Map<String, Object> in, String name) {
        Object value = in.get(name);
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Number num) return new BigDecimal(num.toString());
        throw new TemplateCalculationException("PAYE input '" + name + "' is missing or not a number");
    }

    private static List<BigDecimal> decimals(Map<String, Object> in, String name) {
        if (!(in.get(name) instanceof List<?> list)) {
            throw new TemplateCalculationException("PAYE input '" + name + "' must be a list");
        }
        List<BigDecimal> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof BigDecimal d) out.add(d);
            else if (o instanceof Number num) out.add(new BigDecimal(num.toString()));
            else throw new TemplateCalculationException("PAYE input '" + name + "' must contain numbers");
        }
        return out;
    }
}
