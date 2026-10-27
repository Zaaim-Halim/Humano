package com.humano.repository.hr;

import com.humano.domain.hr.Project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Project} entity.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {}
