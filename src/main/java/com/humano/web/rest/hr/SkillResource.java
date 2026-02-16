package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateSkillRequest;
import com.humano.dto.hr.requests.UpdateSkillRequest;
import com.humano.dto.hr.responses.SkillResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.SkillService;
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
 * REST controller for managing skills.
 */
@RestController
@RequestMapping("/api/hr/skills")
public class SkillResource {

    private static final Logger LOG = LoggerFactory.getLogger(SkillResource.class);
    private static final String ENTITY_NAME = "skill";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SkillService skillService;

    public SkillResource(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * {@code POST  /skills} : Create a new skill.
     *
     * @param request the skill creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new skill
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<SkillResponse> createSkill(@Valid @RequestBody CreateSkillRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Skill: {}", request);

        SkillResponse result = skillService.createSkill(request);

        return ResponseEntity.created(new URI("/api/hr/skills/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /skills/{id}} : Updates an existing skill.
     *
     * @param id the ID of the skill to update
     * @param request the skill update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated skill
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<SkillResponse> updateSkill(@PathVariable UUID id, @Valid @RequestBody UpdateSkillRequest request) {
        LOG.debug("REST request to update Skill: {}", id);

        SkillResponse result = skillService.updateSkill(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /skills} : Get all skills with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of skills in body
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
    public ResponseEntity<Page<SkillResponse>> getAllSkills(Pageable pageable) {
        LOG.debug("REST request to get all Skills");

        Page<SkillResponse> page = skillService.getAllSkills(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /skills/{id}} : Get skill by ID.
     *
     * @param id the ID of the skill to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the skill
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
    public ResponseEntity<SkillResponse> getSkill(@PathVariable UUID id) {
        LOG.debug("REST request to get Skill: {}", id);

        SkillResponse result = skillService.getSkillById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /skills/category/{category}} : Get skills by category.
     *
     * @param category the category to filter by
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of skills in body
     */
    @GetMapping("/category/{category}")
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
    public ResponseEntity<Page<SkillResponse>> getSkillsByCategory(@PathVariable String category, Pageable pageable) {
        LOG.debug("REST request to get Skills by category: {}", category);

        Page<SkillResponse> page = skillService.getSkillsByCategory(category, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /skills/{id}} : Delete skill by ID.
     *
     * @param id the ID of the skill to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteSkill(@PathVariable UUID id) {
        LOG.debug("REST request to delete Skill: {}", id);

        skillService.deleteSkill(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
