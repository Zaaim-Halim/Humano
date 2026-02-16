package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateOrganizationalUnitRequest;
import com.humano.dto.hr.requests.UpdateOrganizationalUnitRequest;
import com.humano.dto.hr.responses.OrganizationalUnitResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.OrganizationalUnitService;
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
 * REST controller for managing organizational units.
 */
@RestController
@RequestMapping("/api/hr/organizational-units")
public class OrganizationalUnitResource {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationalUnitResource.class);
    private static final String ENTITY_NAME = "organizationalUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final OrganizationalUnitService organizationalUnitService;

    public OrganizationalUnitResource(OrganizationalUnitService organizationalUnitService) {
        this.organizationalUnitService = organizationalUnitService;
    }

    /**
     * {@code POST  /organizational-units} : Create a new organizational unit.
     *
     * @param request the organizational unit creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new organizational unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<OrganizationalUnitResponse> createOrganizationalUnit(@Valid @RequestBody CreateOrganizationalUnitRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create OrganizationalUnit: {}", request);

        OrganizationalUnitResponse result = organizationalUnitService.createOrganizationalUnit(request);

        return ResponseEntity.created(new URI("/api/hr/organizational-units/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /organizational-units/{id}} : Updates an existing organizational unit.
     *
     * @param id the ID of the organizational unit to update
     * @param request the organizational unit update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated organizational unit
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<OrganizationalUnitResponse> updateOrganizationalUnit(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateOrganizationalUnitRequest request
    ) {
        LOG.debug("REST request to update OrganizationalUnit: {}", id);

        OrganizationalUnitResponse result = organizationalUnitService.updateOrganizationalUnit(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /organizational-units} : Get all organizational units with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of organizational units in body
     */
    @GetMapping
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<OrganizationalUnitResponse>> getAllOrganizationalUnits(Pageable pageable) {
        LOG.debug("REST request to get all OrganizationalUnits");

        Page<OrganizationalUnitResponse> page = organizationalUnitService.getAllOrganizationalUnits(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /organizational-units/{id}} : Get organizational unit by ID.
     *
     * @param id the ID of the organizational unit to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the organizational unit
     */
    @GetMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<OrganizationalUnitResponse> getOrganizationalUnit(@PathVariable UUID id) {
        LOG.debug("REST request to get OrganizationalUnit: {}", id);

        OrganizationalUnitResponse result = organizationalUnitService.getOrganizationalUnitById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /organizational-units/roots} : Get root organizational units (no parent).
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of root organizational units in body
     */
    @GetMapping("/roots")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<OrganizationalUnitResponse>> getRootOrganizationalUnits(Pageable pageable) {
        LOG.debug("REST request to get root OrganizationalUnits");

        Page<OrganizationalUnitResponse> page = organizationalUnitService.getRootOrganizationalUnits(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /organizational-units/{parentId}/sub-units} : Get sub-units of a parent organizational unit.
     *
     * @param parentId the ID of the parent organizational unit
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of sub-units in body
     */
    @GetMapping("/{parentId}/sub-units")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<OrganizationalUnitResponse>> getSubUnits(@PathVariable UUID parentId, Pageable pageable) {
        LOG.debug("REST request to get sub-units of OrganizationalUnit: {}", parentId);

        Page<OrganizationalUnitResponse> page = organizationalUnitService.getSubUnits(parentId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /organizational-units/{id}} : Delete organizational unit by ID.
     *
     * @param id the ID of the organizational unit to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteOrganizationalUnit(@PathVariable UUID id) {
        LOG.debug("REST request to delete OrganizationalUnit: {}", id);

        organizationalUnitService.deleteOrganizationalUnit(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
