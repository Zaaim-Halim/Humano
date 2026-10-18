package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeDocument} entity.
 */
@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID>, JpaSpecificationExecutor<EmployeeDocument> {
    /** Bytes held by employee documents. */
    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM EmployeeDocument d")
    long totalSizeBytes();

    boolean existsByEmployeeId(UUID employeeId);
}
