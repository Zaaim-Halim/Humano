package com.humano.repository.hr;

import com.humano.domain.hr.TerminationReason;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link TerminationReason} reference entity.
 */
@Repository
public interface TerminationReasonRepository extends JpaRepository<TerminationReason, UUID> {
    Optional<TerminationReason> findByCode(String code);
}
