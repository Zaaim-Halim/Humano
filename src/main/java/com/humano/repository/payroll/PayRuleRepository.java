package com.humano.repository.payroll;

import com.humano.domain.payroll.PayRule;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PayRule} entity.
 */
@Repository
public interface PayRuleRepository extends JpaRepository<PayRule, UUID>, JpaSpecificationExecutor<PayRule> {
    /**
     * Returns the most recent {@code lastModifiedDate} across every {@link PayRule} row,
     * or {@link Optional#empty()} if the table is empty. Used by P3.2 to derive the
     * {@code payRuleVersion} input to the payroll-run idempotency hash &mdash; a single
     * modification anywhere in the rule set advances the version, invalidating the
     * idempotency of any prior run.
     */
    @Query("SELECT MAX(p.audit.lastModifiedDate) FROM PayRule p")
    Optional<Instant> findMaxLastModifiedDate();
}
