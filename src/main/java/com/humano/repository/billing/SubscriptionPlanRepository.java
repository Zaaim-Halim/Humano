package com.humano.repository.billing;

import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.SubscriptionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link SubscriptionPlan} entity.
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID>, JpaSpecificationExecutor<SubscriptionPlan> {
    List<SubscriptionPlan> findByActiveTrue();

    List<SubscriptionPlan> findBySubscriptionType(SubscriptionType subscriptionType);
}
