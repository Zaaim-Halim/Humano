package com.humano.repository.billing;

import com.humano.domain.billing.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Payment} entity.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    List<Payment> findByInvoiceId(UUID invoiceId);

    /**
     * Lookup by provider-side transaction id — used by the Stripe webhook to
     * reconcile asynchronous {@code payment_intent.succeeded /
     * payment_intent.payment_failed} events back to the {@code Payment} row.
     */
    Optional<Payment> findByExternalPaymentId(String externalPaymentId);
}
