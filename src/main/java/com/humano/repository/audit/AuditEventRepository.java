package com.humano.repository.audit;

import com.humano.domain.audit.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Append-only repository for {@link AuditEvent}. Save-only by design — the
 * entity is {@code @Immutable}, so {@code save(...)} on a managed row is a
 * no-op at the ORM layer, and the DB-level triggers on MySQL/MariaDB reject
 * any UPDATE/DELETE that would slip past Hibernate.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    /**
     * Newest-first audit history for a single target. Used by admin views and
     * incident response — never on a request hot path.
     */
    List<AuditEvent> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(String targetType, String targetId, Pageable pageable);
}
