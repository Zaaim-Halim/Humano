package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateBenefitRequest;
import com.humano.dto.hr.requests.UpdateBenefitRequest;
import com.humano.dto.hr.responses.BenefitResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.BenefitService;
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
 * REST controller for managing benefits.
 */
@RestController
@RequestMapping("/api/hr/benefits")
public class BenefitResource {

    private static final Logger LOG = LoggerFactory.getLogger(BenefitResource.class);
    private static final String ENTITY_NAME = "benefit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final BenefitService benefitService;

    public BenefitResource(BenefitService benefitService) {
        this.benefitService = benefitService;
    }

    /**
     * {@code POST  /benefits} : Create a new benefit.
     *
     * @param request the benefit creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new benefit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<BenefitResponse> createBenefit(@Valid @RequestBody CreateBenefitRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Benefit: {}", request);

        BenefitResponse result = benefitService.createBenefit(request);

        return ResponseEntity.created(new URI("/api/hr/benefits/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /benefits/{id}} : Updates an existing benefit.
     *
     * @param id the ID of the benefit to update
     * @param request the benefit update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated benefit
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<BenefitResponse> updateBenefit(@PathVariable UUID id, @Valid @RequestBody UpdateBenefitRequest request) {
        LOG.debug("REST request to update Benefit: {}", id);

        BenefitResponse result = benefitService.updateBenefit(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /benefits} : Get all benefits with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of benefits in body
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
    public ResponseEntity<Page<BenefitResponse>> getAllBenefits(Pageable pageable) {
        LOG.debug("REST request to get all Benefits");

        Page<BenefitResponse> page = benefitService.getAllBenefits(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /benefits/{id}} : Get benefit by ID.
     *
     * @param id the ID of the benefit to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the benefit
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
    public ResponseEntity<BenefitResponse> getBenefit(@PathVariable UUID id) {
        LOG.debug("REST request to get Benefit: {}", id);

        BenefitResponse result = benefitService.getBenefitById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code DELETE  /benefits/{id}} : Delete benefit by ID.
     *
     * @param id the ID of the benefit to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteBenefit(@PathVariable UUID id) {
        LOG.debug("REST request to delete Benefit: {}", id);

        benefitService.deleteBenefit(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
