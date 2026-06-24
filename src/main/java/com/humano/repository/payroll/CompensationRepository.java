package com.humano.repository.payroll;

import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Compensation} entity.
 */
@Repository
public interface CompensationRepository extends JpaRepository<Compensation, UUID>, JpaSpecificationExecutor<Compensation> {
    /** Distinct currencies employees are actually paid in — the source currencies FX ingestion needs. */
    @Query("select distinct c.currency from Compensation c")
    List<Currency> findDistinctCurrencies();
}
