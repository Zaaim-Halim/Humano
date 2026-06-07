package com.humano.service.audit;

import com.humano.domain.audit.AuditEvent;
import com.humano.repository.audit.AuditEventRepository;
import com.humano.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records an immutable {@link AuditEvent} for a sensitive HR/payroll action.
 * <p>
 * No own {@code @Transactional} — the service joins the caller's transaction
 * so that, on rollback, the audit row rolls back together with the audited
 * change. This means a salary change is never persisted without its audit
 * row, and a rolled-back salary change never leaves an orphan audit row.
 * <p>
 * <b>Failure policy.</b>
 * <ul>
 *   <li><b>Fail-closed on core fields</b> — actor/action/target/occurredAt:
 *       missing or invalid → exception → parent transaction rolls back.
 *       Compliance prefers a failed action to an un-audited one.</li>
 *   <li><b>Fail-safe on decorative fields</b> — IP, User-Agent, payload
 *       serialisation: any failure is logged at WARN and the row writes
 *       without the offending field. Off-thread invocations (scheduled
 *       jobs, async listeners) simply produce a row with no IP/UA.</li>
 * </ul>
 */
@Service
public class AuditEventService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEventService.class);
    private static final int USER_AGENT_MAX_LEN = 512;

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Record a sensitive action. Joins the caller's transaction; the row is
     * visible after commit and absent after rollback.
     *
     * @param action     canonical action name (e.g. {@code COMPENSATION_ADJUSTED})
     * @param targetType simple type name of the affected entity (e.g. {@code Compensation})
     * @param targetId   stringified identity of the target row; nullable for mass operations
     * @param payload    free-form key/value snapshot — before/after values, decision metadata, etc.
     */
    public AuditEvent record(String action, String targetType, String targetId, Map<String, Object> payload) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required for audit_event");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("targetType is required for audit_event");
        }
        AuditEvent event = new AuditEvent();
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setActorUserId(SecurityUtils.getCurrentUserId().orElse(null));
        event.setOccurredAt(Instant.now());
        event.setPayloadJson(payload != null ? new HashMap<>(payload) : new HashMap<>());
        applyRequestContext(event);
        return auditEventRepository.save(event);
    }

    /**
     * Best-effort capture of the calling HTTP request's IP and user-agent.
     * Off the request thread (scheduled jobs, async listeners) this is a
     * no-op — the request attributes resolver returns null and we leave
     * both fields unset. Any exception is swallowed with a WARN: a missing
     * IP must never roll back a real action.
     */
    private void applyRequestContext(AuditEvent event) {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) {
                return;
            }
            HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
            event.setIp(resolveClientIp(request));
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > USER_AGENT_MAX_LEN) {
                userAgent = userAgent.substring(0, USER_AGENT_MAX_LEN);
            }
            event.setUserAgent(userAgent);
        } catch (RuntimeException e) {
            LOG.warn("Failed to capture request context for audit_event ({}): {}", event.getAction(), e.getMessage());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
