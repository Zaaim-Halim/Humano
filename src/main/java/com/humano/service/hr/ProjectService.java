package com.humano.service.hr;

import com.humano.domain.hr.Project;
import com.humano.dto.hr.requests.CreateProjectRequest;
import com.humano.dto.hr.requests.UpdateProjectRequest;
import com.humano.dto.hr.responses.ProjectResponse;
import com.humano.repository.hr.ProjectRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing projects.
 * Handles CRUD operations for project entities.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Create a new project.
     *
     * @param request the project creation request
     * @return the created project response
     */
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        log.debug("Request to create Project: {}", request);

        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartTime(request.startTime());
        project.setEndTime(request.endTime());

        Project savedProject = projectRepository.save(project);
        log.info("Created project with ID: {}", savedProject.getId());

        return mapToResponse(savedProject);
    }

    /**
     * Update an existing project.
     *
     * @param id the ID of the project to update
     * @param request the project update request
     * @return the updated project response
     */
    @Transactional
    public ProjectResponse updateProject(UUID id, UpdateProjectRequest request) {
        log.debug("Request to update Project: {}", id);

        return projectRepository
            .findById(id)
            .map(project -> {
                if (request.name() != null) {
                    project.setName(request.name());
                }
                if (request.description() != null) {
                    project.setDescription(request.description());
                }
                if (request.startTime() != null) {
                    project.setStartTime(request.startTime());
                }
                if (request.endTime() != null) {
                    project.setEndTime(request.endTime());
                }
                return mapToResponse(projectRepository.save(project));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Project", id));
    }

    /**
     * Get a project by ID.
     *
     * @param id the ID of the project
     * @return the project response
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID id) {
        log.debug("Request to get Project by ID: {}", id);

        return projectRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Project", id));
    }

    /**
     * Get all projects with pagination.
     *
     * @param pageable pagination information
     * @return page of project responses
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        log.debug("Request to get all Projects");

        return projectRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Delete a project by ID.
     *
     * @param id the ID of the project to delete
     */
    @Transactional
    public void deleteProject(UUID id) {
        log.debug("Request to delete Project: {}", id);

        if (!projectRepository.existsById(id)) {
            throw EntityNotFoundException.create("Project", id);
        }
        projectRepository.deleteById(id);
        log.info("Deleted project with ID: {}", id);
    }

    private ProjectResponse mapToResponse(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStartTime(),
            project.getEndTime(),
            project.getTimesheets() != null ? project.getTimesheets().size() : 0,
            project.getCreatedBy(),
            project.getCreatedDate(),
            project.getLastModifiedBy(),
            project.getLastModifiedDate()
        );
    }
}
