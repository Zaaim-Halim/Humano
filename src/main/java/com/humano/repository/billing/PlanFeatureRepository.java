package com.humano.repository.billing;

import com.humano.domain.billing.PlanFeature;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** What each plan includes of the feature catalog. */
@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {
    @Query("select pf from PlanFeature pf join fetch pf.plan join fetch pf.feature")
    List<PlanFeature> findAllWithPlanAndFeature();
}
