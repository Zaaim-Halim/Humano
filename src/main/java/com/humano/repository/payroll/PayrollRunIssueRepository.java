package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollRunIssue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link PayrollRunIssue} — the employees a run's latest calculation could not pay.
 */
@Repository
public interface PayrollRunIssueRepository extends JpaRepository<PayrollRunIssue, UUID> {
    @Query(
        "SELECT i FROM PayrollRunIssue i JOIN FETCH i.employee WHERE i.run.id = :runId ORDER BY i.employee.lastName, i.employee.firstName"
    )
    List<PayrollRunIssue> findByRunId(@Param("runId") UUID runId);

    long countByRunId(UUID runId);

    @Modifying
    @Query("DELETE FROM PayrollRunIssue i WHERE i.run.id = :runId")
    void deleteByRunId(@Param("runId") UUID runId);
}
