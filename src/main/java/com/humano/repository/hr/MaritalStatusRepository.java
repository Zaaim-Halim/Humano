package com.humano.repository.hr;

import com.humano.domain.hr.MaritalStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link MaritalStatus} reference entity.
 */
@Repository
public interface MaritalStatusRepository extends JpaRepository<MaritalStatus, UUID> {
    Optional<MaritalStatus> findByCode(String code);
}
