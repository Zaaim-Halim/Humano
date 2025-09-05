package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link EmployeeDocument} entity.
 */
@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> , JpaSpecificationExecutor<EmployeeDocument> {
}
