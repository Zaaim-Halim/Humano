package com.humano.service.payroll.template.model;

import com.humano.domain.enumeration.payroll.TaxType;
import java.util.Map;

/**
 * One step of a template's calculation. Steps run in order; each can read the values of the steps
 * before it as {@code #<id>} variables.
 * <ul>
 *   <li>{@link Type#REQUIRE} — {@code condition} must hold, otherwise the calculation stops with
 *   {@code message}. Used for regimes the template doesn't support and conditional inputs; with
 *   {@code detail}, the employee input it asks for, the failure counts as missing details for the
 *   template (so the run can link to them).</li>
 *   <li>{@link Type#VALUE} — binds {@code #<id>} to {@code expr} (an intermediate amount such as
 *   insurable income). With {@code remember}, the value is kept with the payroll result and the next
 *   period reads it as {@code #ytd.last('<id>')} (a figure of a determination that carries over, such as
 *   a withholding rate); numbers only, booleans kept as 1 / 0.</li>
 *   <li>{@link Type#CONTRIBUTION} — applies the {@code employee} and/or {@code employer} side to
 *   {@code base}; binds {@code #<id>_base}, {@code #<id>_employee}, {@code #<id>_employer}.</li>
 *   <li>{@link Type#TAX} — {@code base × rate}, or {@code amount} when the tax isn't a flat rate
 *   (brackets, credits); binds {@code #<id>} and {@code #<id>_base}.</li>
 *   <li>{@link Type#EXTENSION} — calls the code extension {@code extension} with {@code inputs}
 *   (name → expression); binds {@code #<id>} to its outputs map.</li>
 * </ul>
 * A step with a {@code when} condition that evaluates false is skipped; its amounts bind as 0.
 * Zero amounts produce no line unless {@code recordZero} is set (so year-to-date sums of the step's
 * base include periods with nothing due); negative amounts are refused unless {@code allowNegative}
 * is set (cumulative tax refunds).
 *
 * @param id        identifier, also the variable name later steps use
 * @param type      what the step does
 * @param label     payslip label (CONTRIBUTION, TAX)
 * @param ledger    tax type the emitted lines are tagged with (CONTRIBUTION, TAX)
 * @param when      optional condition; the step is skipped when it is false
 * @param condition REQUIRE: what must hold
 * @param message   REQUIRE: the error when it doesn't
 * @param detail    REQUIRE: the employee input key whose absence the condition reports, if that is what it checks
 * @param expr      VALUE: the value
 * @param base      CONTRIBUTION, TAX: the amount rates apply to
 * @param employee  CONTRIBUTION: the employee's share
 * @param employer  CONTRIBUTION: the employer's share
 * @param rate      TAX: flat rate applied to {@code base}
 * @param amount    TAX: the tax, when it isn't {@code base × rate}
 * @param extension EXTENSION: id of the code extension
 * @param inputs    EXTENSION: input name → expression
 * @param rounding  rounding for this step's amounts, overriding the template default
 * @param source    id of the source the step's rule comes from
 * @param recordZero    emit lines even when the amount is zero
 * @param allowNegative accept negative amounts (refunds)
 * @param remember  VALUE: keep the value for the next period ({@code #ytd.last})
 */
public record Step(
    String id,
    Type type,
    String label,
    TaxType ledger,
    String when,
    String condition,
    String message,
    String expr,
    String base,
    Share employee,
    Share employer,
    String rate,
    String amount,
    String extension,
    Map<String, String> inputs,
    PayrollTemplate.Rounding rounding,
    String source,
    boolean recordZero,
    boolean allowNegative,
    String detail,
    boolean remember
) {
    public enum Type {
        REQUIRE,
        VALUE,
        CONTRIBUTION,
        TAX,
        EXTENSION,
    }

    /** One side of a contribution: {@code base × rate}, or {@code amount} when it isn't a flat rate (e.g. bands). */
    public record Share(String rate, String amount) {}
}
