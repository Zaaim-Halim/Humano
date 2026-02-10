package com.humano.repository.hr;

import com.humano.domain.hr.Skill;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Skill} entity.
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {
    Page<Skill> findByCategory(String category, Pageable pageable);
}
