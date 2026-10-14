package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.hr.LeaveRequest;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link LeaveRequest} entity.
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {
    Page<LeaveRequest> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);

    /** Approved leave covering {@code date} for the given employees (team attendance). */
    @Query(
        "SELECT l FROM LeaveRequest l WHERE l.status = :status AND l.startDate <= :date AND l.endDate >= :date AND l.employee.id IN :employeeIds"
    )
    List<LeaveRequest> findCovering(
        @Param("status") LeaveStatus status,
        @Param("date") LocalDate date,
        @Param("employeeIds") Collection<UUID> employeeIds
    );
}
