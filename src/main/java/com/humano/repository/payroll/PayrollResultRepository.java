package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollResult;
import com.humano.repository.payroll.projection.RunTotalsRow;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PayrollResult} entity.
 */
@Repository
public interface PayrollResultRepository extends JpaRepository<PayrollResult, UUID>, JpaSpecificationExecutor<PayrollResult> {
    List<PayrollResult> findByEmployeeId(UUID employeeId);

    /**
     * Totals for several runs in one GROUP BY — one row per (run, native currency). Amounts use
     * the reporting-currency figures when the run has a reporting currency (falling back to the
     * native figure if a result has none), mirroring the single-run summary.
     */
    @Query(
        """
        SELECT new com.humano.repository.payroll.projection.RunTotalsRow(
            run.id, c.code, COUNT(r),
            SUM(CASE WHEN run.reportingCurrency IS NOT NULL THEN COALESCE(r.reportingGross, r.gross) ELSE r.gross END),
            SUM(CASE WHEN run.reportingCurrency IS NOT NULL THEN COALESCE(r.reportingTotalDeductions, r.totalDeductions) ELSE r.totalDeductions END),
            SUM(CASE WHEN run.reportingCurrency IS NOT NULL THEN COALESCE(r.reportingNet, r.net) ELSE r.net END),
            SUM(CASE WHEN run.reportingCurrency IS NOT NULL THEN COALESCE(r.reportingEmployerCost, r.employerCost) ELSE r.employerCost END)
        )
        FROM PayrollResult r JOIN r.run run JOIN r.currency c
        WHERE run.id IN :runIds
        GROUP BY run.id, c.code
        """
    )
    List<RunTotalsRow> totalsForRuns(@Param("runIds") Collection<UUID> runIds);
}
