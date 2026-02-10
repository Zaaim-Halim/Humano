package com.humano.repository.billing;

import com.humano.domain.billing.Invoice;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Invoice} entity.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    List<Invoice> findByTenantId(UUID tenantId);

    List<Invoice> findBySubscriptionId(UUID subscriptionId);

    List<Invoice> findBySubscriptionIdAndStatus(UUID subscriptionId, InvoiceStatus status);

    List<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);

    List<Invoice> findByStatus(InvoiceStatus status);
}
