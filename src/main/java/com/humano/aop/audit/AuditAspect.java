package com.humano.aop.audit;

import com.humano.service.audit.AuditEventService;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Honours {@link Auditable} on Spring-managed beans by writing an audit row
 * after the method returns successfully. The advice runs the target method
 * first, then records the audit — so a thrown method exits without an audit
 * row (correct: only completed actions are audited).
 * <p>
 * The audit write joins the caller's transaction (see
 * {@link AuditEventService}); on rollback of the caller's tx the audit row
 * rolls back with it.
 * <p>
 * Audit-side failures NEVER mask the method's return value: any exception
 * from {@code AuditEventService.record} is caught and logged at ERROR. The
 * compliance trade-off is deliberate — a stuck audit subsystem must not
 * break the platform. For environments where audit must be hard-required,
 * remove the catch and let it propagate.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger LOG = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditEventService auditEventService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditAspect(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @Around("@annotation(auditable)")
    public Object recordAudit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            String targetId = evaluateTargetId(joinPoint, auditable, result);
            Map<String, Object> payload = buildPayload(joinPoint, result);
            auditEventService.record(auditable.action(), auditable.targetType(), targetId, payload);
        } catch (RuntimeException e) {
            LOG.error(
                "Failed to record audit_event for action={} targetType={} method={}: {}",
                auditable.action(),
                auditable.targetType(),
                joinPoint.getSignature().toShortString(),
                e.getMessage(),
                e
            );
        }
        return result;
    }

    /**
     * Evaluates the {@code targetIdExpression} SpEL against the method's
     * args and {@code #result}. Returns {@code null} when the expression is
     * empty, the parse fails, or the evaluation produces {@code null} — the
     * caller treats a null target_id as "mass operation".
     */
    private String evaluateTargetId(ProceedingJoinPoint joinPoint, Auditable auditable, Object result) {
        String expression = auditable.targetIdExpression();
        if (expression.isBlank()) {
            return null;
        }
        try {
            EvaluationContext context = buildContext(joinPoint, result);
            Expression compiled = parser.parseExpression(expression);
            Object value = compiled.getValue(context);
            return value != null ? value.toString() : null;
        } catch (RuntimeException e) {
            LOG.warn(
                "Failed to evaluate targetIdExpression='{}' for {}: {}",
                expression,
                joinPoint.getSignature().toShortString(),
                e.getMessage()
            );
            return null;
        }
    }

    /**
     * Default payload: the return value under {@code result}. Methods that
     * need richer payloads (before/after) should call
     * {@code AuditEventService.record} directly instead.
     */
    private Map<String, Object> buildPayload(ProceedingJoinPoint joinPoint, Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result != null) {
            payload.put("result", result.toString());
        }
        payload.put("method", joinPoint.getSignature().toShortString());
        return payload;
    }

    private EvaluationContext buildContext(ProceedingJoinPoint joinPoint, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Map<String, Object> argMap = new HashMap<>();
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                context.setVariable(names[i], args[i]);
                argMap.put(names[i], args[i]);
            }
        }
        context.setVariable("args", args);
        context.setVariable("result", result);
        context.setRootObject(argMap);
        return context;
    }
}
