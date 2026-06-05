package com.humano.repository.payroll;

import com.humano.domain.payroll.TaxBracket;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link TaxBracket} entity.
 */
@Repository
public interface TaxBracketRepository extends JpaRepository<TaxBracket, UUID>, JpaSpecificationExecutor<TaxBracket> {
    /**
     * Returns the most recent {@code lastModifiedDate} across every {@link TaxBracket}
     * row, or {@link Optional#empty()} if the table is empty. Used to derive the
     * {@code taxBracketVersion} input to the payroll-run idempotency hash.
     */
    @Query("SELECT MAX(t.audit.lastModifiedDate) FROM TaxBracket t")
    Optional<Instant> findMaxLastModifiedDate();
}
