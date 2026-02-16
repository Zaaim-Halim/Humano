package com.humano.repository.hr;

import com.humano.domain.hr.EmployeePositionHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for EmployeePositionHistory entity.
 */
@Repository
public interface EmployeePositionHistoryRepository extends JpaRepository<EmployeePositionHistory, UUID> {
    /**
     * Find position history by employee ID, ordered by date descending.
     */
    Page<EmployeePositionHistory> findByEmployeeIdOrderByEffectiveDateDesc(UUID employeeId, Pageable pageable);

    /**
     * Find position history by employee ID.
     */
    List<EmployeePositionHistory> findByEmployeeId(UUID employeeId);

    /**
     * Find position history within a date range.
     */
    List<EmployeePositionHistory> findByEmployeeIdAndEffectiveDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate);

    /**
     * Find the latest position history entry for an employee.
     */
    EmployeePositionHistory findFirstByEmployeeIdOrderByEffectiveDateDesc(UUID employeeId);

    /**
     * Find position history by department.
     */
    List<EmployeePositionHistory> findByDepartmentId(UUID departmentId);

    /**
     * Find position history by position.
     */
    List<EmployeePositionHistory> findByPositionId(UUID positionId);
}
