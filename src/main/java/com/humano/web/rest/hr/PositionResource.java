package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreatePositionRequest;
import com.humano.dto.hr.requests.UpdatePositionRequest;
import com.humano.dto.hr.responses.PositionResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.PositionService;
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
 * REST controller for managing positions.
 */
@RestController
@RequestMapping("/api/hr/positions")
public class PositionResource {

    private static final Logger LOG = LoggerFactory.getLogger(PositionResource.class);
    private static final String ENTITY_NAME = "position";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PositionService positionService;

    public PositionResource(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * {@code POST  /positions} : Create a new position.
     *
     * @param request the position creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new position
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<PositionResponse> createPosition(@Valid @RequestBody CreatePositionRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Position: {}", request);

        PositionResponse result = positionService.createPosition(request);

        return ResponseEntity.created(new URI("/api/hr/positions/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /positions/{id}} : Updates an existing position.
     *
     * @param id the ID of the position to update
     * @param request the position update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated position
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<PositionResponse> updatePosition(@PathVariable UUID id, @Valid @RequestBody UpdatePositionRequest request) {
        LOG.debug("REST request to update Position: {}", id);

        PositionResponse result = positionService.updatePosition(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /positions} : Get all positions with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of positions in body
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
    public ResponseEntity<Page<PositionResponse>> getAllPositions(Pageable pageable) {
        LOG.debug("REST request to get all Positions");

        Page<PositionResponse> page = positionService.getAllPositions(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /positions/{id}} : Get position by ID.
     *
     * @param id the ID of the position to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the position
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
    public ResponseEntity<PositionResponse> getPosition(@PathVariable UUID id) {
        LOG.debug("REST request to get Position: {}", id);

        PositionResponse result = positionService.getPositionById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /positions/unit/{unitId}} : Get positions by organizational unit.
     *
     * @param unitId the ID of the organizational unit
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of positions in body
     */
    @GetMapping("/unit/{unitId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<PositionResponse>> getPositionsByUnit(@PathVariable UUID unitId, Pageable pageable) {
        LOG.debug("REST request to get Positions by Unit: {}", unitId);

        Page<PositionResponse> page = positionService.getPositionsByUnit(unitId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /positions/{id}} : Delete position by ID.
     *
     * @param id the ID of the position to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deletePosition(@PathVariable UUID id) {
        LOG.debug("REST request to delete Position: {}", id);

        positionService.deletePosition(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
