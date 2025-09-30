package com.humano.repository.billing;

import com.humano.domain.billing.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

/**
 * Spring Data JPA repository for the {@link Payment} entity.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    List<Payment> findByInvoiceId(UUID invoiceId);
    List<Payment> findByTenantId(UUID tenantId);
}
