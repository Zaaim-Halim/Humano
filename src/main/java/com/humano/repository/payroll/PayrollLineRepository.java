package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollLine;
import com.humano.repository.payroll.projection.TemplateYtdRow;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PayrollLine} entity.
 * <p>
 * Filtering by {@code PayrollResult} happens via {@link JpaSpecificationExecutor} in
 * {@code PayrollProcessingService}.
 */
@Repository
public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID>, JpaSpecificationExecutor<PayrollLine> {
    /**
     * Per step and line category, the bases, amounts and line count a payroll template (any version)
     * produced for an employee in POSTED runs whose period starts in {@code [from, before)} — the
     * template's year-to-date before the period being calculated.
     */
    @Query(
        """
        SELECT new com.humano.repository.payroll.projection.TemplateYtdRow(
            l.templateStep, l.lineCategory, SUM(COALESCE(l.quantity, 0)), SUM(l.amount), COUNT(l)
        )
        FROM PayrollLine l JOIN l.result r JOIN r.run run JOIN r.payrollPeriod p
        WHERE r.employee.id = :employeeId
          AND l.templateRef LIKE :templateRefPrefix
          AND l.templateStep IS NOT NULL
          AND run.status = com.humano.domain.enumeration.payroll.RunStatus.POSTED
          AND p.startDate >= :from
          AND p.startDate < :before
        GROUP BY l.templateStep, l.lineCategory
        """
    )
    List<TemplateYtdRow> sumTemplateLines(
        @Param("employeeId") UUID employeeId,
        @Param("templateRefPrefix") String templateRefPrefix,
        @Param("from") LocalDate from,
        @Param("before") LocalDate before
    );

    /** As {@link #sumTemplateLines}, but placing periods in the tax year by their payment date. */
    @Query(
        """
        SELECT new com.humano.repository.payroll.projection.TemplateYtdRow(
            l.templateStep, l.lineCategory, SUM(COALESCE(l.quantity, 0)), SUM(l.amount), COUNT(l)
        )
        FROM PayrollLine l JOIN l.result r JOIN r.run run JOIN r.payrollPeriod p
        WHERE r.employee.id = :employeeId
          AND l.templateRef LIKE :templateRefPrefix
          AND l.templateStep IS NOT NULL
          AND run.status = com.humano.domain.enumeration.payroll.RunStatus.POSTED
          AND p.paymentDate >= :from
          AND p.paymentDate < :before
        GROUP BY l.templateStep, l.lineCategory
        """
    )
    List<TemplateYtdRow> sumTemplateLinesByPaymentDate(
        @Param("employeeId") UUID employeeId,
        @Param("templateRefPrefix") String templateRefPrefix,
        @Param("from") LocalDate from,
        @Param("before") LocalDate before
    );
}
