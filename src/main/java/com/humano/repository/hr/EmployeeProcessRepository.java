package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.EmployeeProcessType;
import com.humano.domain.hr.EmployeeProcess;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for EmployeeProcess entity.
 */
@Repository
public interface EmployeeProcessRepository extends JpaRepository<EmployeeProcess, UUID> {
    /**
     * Find processes by employee.
     */
    List<EmployeeProcess> findByEmployeeId(UUID employeeId);

    /**
     * Find processes by employee and type.
     */
    Optional<EmployeeProcess> findByEmployeeIdAndProcessType(UUID employeeId, EmployeeProcessType type);

    /**
     * Find active process for employee.
     */
    Optional<EmployeeProcess> findByEmployeeIdAndProcessTypeAndStatusIn(
        UUID employeeId,
        EmployeeProcessType type,
        List<EmployeeProcessStatus> statuses
    );

    /**
     * Find processes by status.
     */
    Page<EmployeeProcess> findByStatus(EmployeeProcessStatus status, Pageable pageable);

    /**
     * Find processes by type and status.
     */
    Page<EmployeeProcess> findByProcessTypeAndStatus(EmployeeProcessType type, EmployeeProcessStatus status, Pageable pageable);

    /**
     * Find processes by type excluding a status.
     */
    Page<EmployeeProcess> findByProcessTypeAndStatusNot(EmployeeProcessType type, EmployeeProcessStatus status, Pageable pageable);

    /**
     * Find processes excluding a status.
     */
    Page<EmployeeProcess> findByStatusNot(EmployeeProcessStatus status, Pageable pageable);

    /**
     * Find processes starting in date range.
     */
    @Query("SELECT p FROM EmployeeProcess p WHERE p.startDate BETWEEN :startDate AND :endDate")
    List<EmployeeProcess> findByStartDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Count processes by type and status.
     */
    long countByProcessTypeAndStatus(EmployeeProcessType type, EmployeeProcessStatus status);

    /**
     * Find overdue processes.
     */
    @Query("SELECT p FROM EmployeeProcess p WHERE p.targetEndDate < :today AND p.status IN :activeStatuses")
    List<EmployeeProcess> findOverdueProcesses(
        @Param("today") LocalDate today,
        @Param("activeStatuses") List<EmployeeProcessStatus> activeStatuses
    );
}
