package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.hr.OvertimeRecord;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link OvertimeRecord} entity.
 */
@Repository
public interface OvertimeRecordRepository extends JpaRepository<OvertimeRecord, UUID>, JpaSpecificationExecutor<OvertimeRecord> {
    Page<OvertimeRecord> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<OvertimeRecord> findByApprovalStatus(OvertimeApprovalStatus status, Pageable pageable);

    Page<OvertimeRecord> findByEmployeeIdAndDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
