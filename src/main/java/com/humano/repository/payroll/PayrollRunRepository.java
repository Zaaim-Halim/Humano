package com.humano.repository.payroll;

import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.payroll.Currency;
import com.humano.domain.payroll.PayrollRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PayrollRun} entity.
 */
@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID>, JpaSpecificationExecutor<PayrollRun> {
    /** Distinct reporting currencies configured on runs — extra target currencies FX ingestion needs. */
    @Query("select distinct r.reportingCurrency from PayrollRun r where r.reportingCurrency is not null")
    List<Currency> findDistinctReportingCurrencies();

    boolean existsByPeriodIdAndStatus(UUID periodId, RunStatus status);

    /** Run history page — period and reporting currency fetched in the same query. */
    @EntityGraph(attributePaths = { "period", "reportingCurrency", "approvedBy" })
    @Query(value = "SELECT r FROM PayrollRun r", countQuery = "SELECT COUNT(r) FROM PayrollRun r")
    Page<PayrollRun> findHistory(Pageable pageable);
}
