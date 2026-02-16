package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeProcessTask;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for EmployeeProcessTask entity.
 */
@Repository
public interface EmployeeProcessTaskRepository extends JpaRepository<EmployeeProcessTask, UUID> {
    /**
     * Find tasks by process ID.
     */
    List<EmployeeProcessTask> findByProcessId(UUID processId);

    /**
     * Find incomplete tasks by process ID.
     */
    List<EmployeeProcessTask> findByProcessIdAndCompletedFalse(UUID processId);

    /**
     * Find tasks assigned to an employee.
     */
    List<EmployeeProcessTask> findByAssignedToId(UUID assignedToId);

    /**
     * Find incomplete tasks assigned to an employee.
     */
    List<EmployeeProcessTask> findByAssignedToIdAndCompletedFalse(UUID assignedToId);

    /**
     * Find overdue tasks.
     */
    List<EmployeeProcessTask> findByDueDateBeforeAndCompletedFalse(LocalDate date);

    /**
     * Find tasks due within a date range.
     */
    List<EmployeeProcessTask> findByDueDateBetweenAndCompletedFalse(LocalDate startDate, LocalDate endDate);

    /**
     * Count incomplete tasks by process.
     */
    long countByProcessIdAndCompletedFalse(UUID processId);

    /**
     * Count completed tasks by process.
     */
    long countByProcessIdAndCompletedTrue(UUID processId);
}
