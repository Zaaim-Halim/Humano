package com.humano.repository.hr;

import com.humano.domain.hr.Address;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Address} entity.
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByEmployeeId(UUID employeeId);
}
