package com.humano.repository.billing;

import com.humano.domain.billing.SubscriptionAddon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionAddonRepository extends JpaRepository<SubscriptionAddon, UUID> {
    List<SubscriptionAddon> findBySubscriptionId(UUID subscriptionId);

    Optional<SubscriptionAddon> findBySubscriptionIdAndFeatureCode(UUID subscriptionId, String featureCode);

    /**
     * Add-ons of yearly subscriptions that are running and due for their monthly invoice (with a
     * monthly subscription they are billed on its renewal instead).
     */
    @Query(
        "SELECT a FROM SubscriptionAddon a JOIN FETCH a.subscription s JOIN FETCH s.tenant WHERE a.quantity > 0 " +
        "AND s.billingCycle = com.humano.domain.enumeration.billing.BillingCycle.YEARLY " +
        "AND s.status IN (com.humano.domain.enumeration.billing.SubscriptionStatus.ACTIVE, com.humano.domain.enumeration.billing.SubscriptionStatus.PAST_DUE) " +
        "AND (a.billedUntil IS NULL OR a.billedUntil <= :now)"
    )
    List<SubscriptionAddon> findDueForMonthlyInvoice(@Param("now") java.time.Instant now);
}
