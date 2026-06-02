package com.humano.repository.billing;

import com.humano.domain.billing.Feature;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Feature} entity.
 * <p>
 * {@code SubscriptionPlan} owns the relationship unidirectionally
 * ({@code @OneToMany @JoinColumn(name = "subscription_plan_id")}); {@link Feature} has no
 * back-reference, so the lookup is expressed via JPQL on the owning side.
 */
@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID>, JpaSpecificationExecutor<Feature> {
    @Query("select f from SubscriptionPlan sp join sp.features f where sp.id = :planId")
    List<Feature> findBySubscriptionPlanId(@Param("planId") UUID planId);
}
