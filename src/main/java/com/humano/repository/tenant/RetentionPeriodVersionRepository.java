package com.humano.repository.tenant;

import com.humano.domain.tenant.RetentionPeriodVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RetentionPeriodVersionRepository extends JpaRepository<RetentionPeriodVersion, UUID> {
    List<RetentionPeriodVersion> findAllByOrderByEffectiveFromAsc();

    /** Scheduled changes not yet in force. */
    List<RetentionPeriodVersion> findByEffectiveFromAfter(Instant instant);
}
