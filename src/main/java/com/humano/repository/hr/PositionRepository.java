package com.humano.repository.hr;

import com.humano.domain.hr.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Position} entity.
 */
@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> , JpaSpecificationExecutor<Position> {
}
