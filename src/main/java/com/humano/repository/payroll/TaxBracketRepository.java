package com.humano.repository.payroll;

import com.humano.domain.payroll.TaxBracket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link TaxBracket} entity.
 */
@Repository
public interface TaxBracketRepository extends JpaRepository<TaxBracket, UUID>, JpaSpecificationExecutor<TaxBracket> {
}
