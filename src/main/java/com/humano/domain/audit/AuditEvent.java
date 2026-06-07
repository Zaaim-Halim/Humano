package com.humano.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of a sensitive action performed inside the tenant.
 * <p>
 * Insert-only at the ORM layer via {@link Immutable}; UPDATE/DELETE are
 * additionally blocked at the DB layer on MySQL/MariaDB through the
 * {@code audit_event_no_update} / {@code audit_event_no_delete} triggers
 * defined in {@code 20260607-audit-event-changelog.xml}. On H2 (dev/test)
 * only the ORM guard is active.
 * <p>
 * {@link #actorUserId} holds the principal's UUID resolved from the
 * {@code AuthenticatedUser} principal that {@code DomainUserDetailsService}
 * attaches at login — no DB lookup at write time. Deliberately NOT declared
 * as a foreign-key to {@code app_user(id)}: the audit row must outlive its
 * actor's row (compliance: deleting a user does not erase their audit
 * history) and a missing-user failure must never roll back the audited
 * action.
 * <p>
 * {@link #payloadJson} is mapped through Hibernate's
 * {@link JdbcTypeCode}{@code (SqlTypes.JSON)} against the {@code ${jsonType}}
 * Liquibase property, so the column is native JSON on MySQL/MariaDB/H2 and
 * JSONB on PostgreSQL.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The UUID of the user who performed the action, or {@code null} when the
     * action was triggered by a scheduled job / system context with no
     * authenticated principal.
     */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /**
     * Canonical action name — uppercase snake-case, e.g.
     * {@code COMPENSATION_ADJUSTED}, {@code PAYROLL_POSTED},
     * {@code USER_ROLES_CHANGED}. Free-form by spec, but stable values let
     * dashboards / alerting key off the column.
     */
    @Column(name = "action", length = 64, nullable = false)
    private String action;

    /**
     * The kind of entity the action operated on — typically the simple class
     * name (e.g. {@code Compensation}, {@code PayrollRun}, {@code User}).
     */
    @Column(name = "target_type", length = 128, nullable = false)
    private String targetType;

    /**
     * Stringified identity of the target — usually a UUID, may be a login or
     * other natural key. Nullable for actions that aren't scoped to a single
     * row (mass operations).
     */
    @Column(name = "target_id", length = 128)
    private String targetId;

    /**
     * Free-form JSON payload — before/after snapshots, request metadata,
     * decision context. Stored as native JSON.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "json")
    private Map<String, Object> payloadJson = new HashMap<>();

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /**
     * Client IP captured from the current request, or {@code null} when the
     * action ran off the request thread (scheduled job, async listener).
     */
    @Column(name = "ip", length = 45)
    private String ip;

    /**
     * Client {@code User-Agent} captured from the current request, or
     * {@code null} when off the request thread. Truncated to 512 chars.
     */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Map<String, Object> getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(Map<String, Object> payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
