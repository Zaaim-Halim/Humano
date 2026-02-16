package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateProjectRequest;
import com.humano.dto.hr.requests.UpdateProjectRequest;
import com.humano.dto.hr.responses.ProjectResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing projects.
 */
@RestController
@RequestMapping("/api/hr/projects")
public class ProjectResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectResource.class);
    private static final String ENTITY_NAME = "project";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProjectService projectService;

    public ProjectResource(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * {@code POST  /projects} : Create a new project.
     *
     * @param request the project creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new project
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Project: {}", request);

        ProjectResponse result = projectService.createProject(request);

        return ResponseEntity.created(new URI("/api/hr/projects/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /projects/{id}} : Updates an existing project.
     *
     * @param id the ID of the project to update
     * @param request the project update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated project
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        LOG.debug("REST request to update Project: {}", id);

        ProjectResponse result = projectService.updateProject(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /projects} : Get all projects with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of projects in body
     */
    @GetMapping
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Page<ProjectResponse>> getAllProjects(Pageable pageable) {
        LOG.debug("REST request to get all Projects");

        Page<ProjectResponse> page = projectService.getAllProjects(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /projects/{id}} : Get project by ID.
     *
     * @param id the ID of the project to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the project
     */
    @GetMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
        LOG.debug("REST request to get Project: {}", id);

        ProjectResponse result = projectService.getProjectById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code DELETE  /projects/{id}} : Delete project by ID.
     *
     * @param id the ID of the project to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        LOG.debug("REST request to delete Project: {}", id);

        projectService.deleteProject(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
