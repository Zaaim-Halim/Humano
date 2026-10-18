package com.humano.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

/**
 * The workspace's plan does not allow this: a feature it does not include ({@code error.featurenotinplan})
 * or a limit it has reached ({@code error.limitreached}). 403, with the feature code (and the limit) so
 * the client can react, and a title written for the person who hit it — screens show it as is.
 */
@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class PlanLimitException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;
    private static final String WHERE = "An admin can choose a plan in Settings → Billing.";

    private PlanLimitException(String title, String errorKey, String feature, Integer limit) {
        super(
            HttpStatus.FORBIDDEN,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(HttpStatus.FORBIDDEN.value())
                .withType(ErrorConstants.DEFAULT_TYPE)
                .withTitle(title)
                .withProperty("message", "error." + errorKey)
                .withProperty("feature", feature)
                .withProperty("limit", limit)
                .build(),
            null
        );
    }

    /**
     * @param name the feature as people know it ("Payroll runs & payslips")
     * @param plan the plan's name, or {@code null}
     */
    public static PlanLimitException notInPlan(String feature, String name, String plan) {
        String what = plan != null ? "Your " + plan + " plan doesn't include " + name + "." : name + " isn't included in your plan.";
        return new PlanLimitException(what + " You can still view what's here. " + WHERE, "featurenotinplan", feature, null);
    }

    /**
     * @param unit what is counted ("employees")
     * @param plan the plan's name, or {@code null}
     */
    public static PlanLimitException limitReached(String feature, Integer limit, String unit, String plan) {
        String what = (plan != null ? "Your " + plan + " plan" : "Your plan") + " allows " + limit + " " + unit + ".";
        return new PlanLimitException(what + " " + WHERE, "limitreached", feature, limit);
    }
}
