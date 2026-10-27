package com.humano.repository.hr;

import com.humano.domain.hr.MeritGuidelines;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@link MeritGuidelines} singleton (one row per tenant schema). Fetch the current
 * guidelines with {@code findAll().stream().findFirst()}.
 */
@Repository
public interface MeritGuidelinesRepository extends JpaRepository<MeritGuidelines, UUID> {}
