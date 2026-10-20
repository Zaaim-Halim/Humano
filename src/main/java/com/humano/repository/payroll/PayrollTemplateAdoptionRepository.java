package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollTemplateAdoption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link PayrollTemplateAdoption} — at most one row per template id.
 */
@Repository
public interface PayrollTemplateAdoptionRepository extends JpaRepository<PayrollTemplateAdoption, UUID> {
    Optional<PayrollTemplateAdoption> findByTemplateId(String templateId);

    List<PayrollTemplateAdoption> findByEnabledTrue();
}
