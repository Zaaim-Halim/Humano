package com.humano.repository.billing;

import com.humano.domain.billing.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Feature} entity.
 */
@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID>, JpaSpecificationExecutor<Feature> {
}
