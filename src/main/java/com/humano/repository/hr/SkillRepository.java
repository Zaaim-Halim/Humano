package com.humano.repository.hr;

import com.humano.domain.hr.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Skill} entity.
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {
}
