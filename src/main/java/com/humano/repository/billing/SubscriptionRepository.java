package com.humano.repository.billing;

import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Subscription} entity.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID>, JpaSpecificationExecutor<Subscription> {
    Optional<Subscription> findByTenantId(UUID tenantId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    List<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

    /**
     * Find subscriptions that are due for renewal (period ending soon).
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = :status AND s.currentPeriodEnd <= :beforeDate AND s.autoRenew = true")
    List<Subscription> findSubscriptionsDueForRenewal(@Param("status") SubscriptionStatus status, @Param("beforeDate") Instant beforeDate);

    /**
     * Find trial subscriptions expiring soon.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' AND s.trialEnd <= :beforeDate")
    List<Subscription> findExpiringTrials(@Param("beforeDate") Instant beforeDate);

    /**
     * Find subscriptions marked for cancellation at period end.
     */
    List<Subscription> findByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(Instant date);
}
