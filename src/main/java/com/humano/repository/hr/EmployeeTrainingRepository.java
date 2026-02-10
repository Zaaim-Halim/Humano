package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeTraining;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeTraining} entity.
 */
@Repository
public interface EmployeeTrainingRepository extends JpaRepository<EmployeeTraining, UUID>, JpaSpecificationExecutor<EmployeeTraining> {
    Page<EmployeeTraining> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<EmployeeTraining> findByTrainingId(UUID trainingId, Pageable pageable);
}
