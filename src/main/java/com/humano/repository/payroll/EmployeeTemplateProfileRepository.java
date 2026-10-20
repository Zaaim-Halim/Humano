package com.humano.repository.payroll;

import com.humano.domain.payroll.EmployeeTemplateProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeTemplateProfile} — at most one row per (employee, template).
 */
@Repository
public interface EmployeeTemplateProfileRepository extends JpaRepository<EmployeeTemplateProfile, UUID> {
    Optional<EmployeeTemplateProfile> findByEmployeeIdAndTemplateId(UUID employeeId, String templateId);

    long countByTemplateId(String templateId);

    /** Latest change to any profile; part of the payroll-run idempotency hash so edits trigger a recalculation. */
    @Query("SELECT MAX(x.lastModifiedDate) FROM EmployeeTemplateProfile x")
    Optional<Instant> findMaxLastModifiedDate();
}
