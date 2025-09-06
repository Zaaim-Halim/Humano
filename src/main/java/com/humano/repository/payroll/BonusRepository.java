package com.humano.repository.payroll;

import com.humano.domain.payroll.Bonus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Bonus} entity.
 */
@Repository
public interface BonusRepository extends JpaRepository<Bonus, UUID>, JpaSpecificationExecutor<Bonus> {
}
