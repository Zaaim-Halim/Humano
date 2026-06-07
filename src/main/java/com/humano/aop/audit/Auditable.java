package com.humano.aop.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful invocation should produce an
 * {@code audit_event} row. The {@link AuditAspect} runs after the method
 * commits (the audit row joins the caller's transaction) and uses the
 * declared values + a SpEL evaluation against the method's args and return
 * value to populate the audit fields.
 * <p>
 * Use this for actions where the "after" state is sufficient — payroll
 * postings, role assignments, tenant-status changes. For actions that
 * require a before/after diff (salary adjustment), call
 * {@link com.humano.service.audit.AuditEventService#record} directly
 * from inside the method so both values are captured in one place.
 *
 * <pre>{@code
 *   @Auditable(
 *       action = "PAYROLL_POSTED",
 *       targetType = "PayrollRun",
 *       targetIdExpression = "#runId"
 *   )
 *   public PayrollRunResponse postPayrollRun(UUID runId) { ... }
 * }</pre>
 *
 * SpEL root context exposes the method's arguments by name (requires
 * {@code -parameters} javac flag — Spring Boot's default) and the method's
 * return value as {@code #result}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    /**
     * Canonical action name written to {@code audit_event.action}.
     */
    String action();

    /**
     * Simple type name of the audited entity written to
     * {@code audit_event.target_type}.
     */
    String targetType();

    /**
     * Optional SpEL evaluated against the method's args and {@code #result}
     * to produce {@code audit_event.target_id}. Empty (default) leaves the
     * target_id null.
     */
    String targetIdExpression() default "";
}
