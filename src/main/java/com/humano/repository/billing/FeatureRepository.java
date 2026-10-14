package com.humano.repository.billing;

import com.humano.domain.billing.Feature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the plan feature catalog ({@link Feature}). */
@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID>, JpaSpecificationExecutor<Feature> {
    /** Catalog order (categories appear in the order of their first feature). */
    List<Feature> findAllByOrderBySortOrderAscNameAsc();

    Optional<Feature> findOneByCode(String code);
}
