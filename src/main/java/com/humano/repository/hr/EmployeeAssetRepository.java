package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeAsset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeAsset} entity.
 */
@Repository
public interface EmployeeAssetRepository extends JpaRepository<EmployeeAsset, UUID> {
    List<EmployeeAsset> findByEmployeeId(UUID employeeId);
}
