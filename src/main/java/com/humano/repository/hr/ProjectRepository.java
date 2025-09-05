package com.humano.repository.hr;

import com.humano.domain.hr.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Project} entity.
 */
@Repository
public interface  ProjectRepository extends JpaRepository<Project, UUID> {
}
