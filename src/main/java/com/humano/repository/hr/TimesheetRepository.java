package com.humano.repository.hr;

import com.humano.domain.hr.Timesheet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Timesheet} entity.
 */
@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, UUID>, JpaSpecificationExecutor<Timesheet> {
    Page<Timesheet> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<Timesheet> findByProjectId(UUID projectId, Pageable pageable);

    Page<Timesheet> findByEmployeeIdAndDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query(
        "SELECT COALESCE(SUM(t.hoursWorked), 0) FROM Timesheet t WHERE t.employee.id = :employeeId AND t.date BETWEEN :startDate AND :endDate"
    )
    BigDecimal sumHoursWorkedByEmployeeIdAndDateBetween(
        @Param("employeeId") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
