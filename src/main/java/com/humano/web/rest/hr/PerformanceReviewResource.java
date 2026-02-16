package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreatePerformanceReviewRequest;
import com.humano.dto.hr.requests.PerformanceReviewSearchRequest;
import com.humano.dto.hr.requests.UpdatePerformanceReviewRequest;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.PerformanceReviewService;
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
 * REST controller for managing performance reviews.
 */
@RestController
@RequestMapping("/api/hr/performance-reviews")
public class PerformanceReviewResource {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceReviewResource.class);
    private static final String ENTITY_NAME = "performanceReview";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PerformanceReviewService performanceReviewService;

    public PerformanceReviewResource(PerformanceReviewService performanceReviewService) {
        this.performanceReviewService = performanceReviewService;
    }

    /**
     * {@code POST  /performance-reviews} : Create a new performance review.
     *
     * @param request the performance review creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new performance review
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<PerformanceReviewResponse> createPerformanceReview(@Valid @RequestBody CreatePerformanceReviewRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create PerformanceReview: {}", request);

        PerformanceReviewResponse result = performanceReviewService.createPerformanceReview(request);

        return ResponseEntity.created(new URI("/api/hr/performance-reviews/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /performance-reviews/{id}} : Updates an existing performance review.
     *
     * @param id the ID of the performance review to update
     * @param request the performance review update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated performance review
     */
    @PutMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<PerformanceReviewResponse> updatePerformanceReview(
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePerformanceReviewRequest request
    ) {
        LOG.debug("REST request to update PerformanceReview: {}", id);

        PerformanceReviewResponse result = performanceReviewService.updatePerformanceReview(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /performance-reviews} : Get all performance reviews with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of performance reviews in body
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
    public ResponseEntity<Page<PerformanceReviewResponse>> getAllPerformanceReviews(Pageable pageable) {
        LOG.debug("REST request to get all PerformanceReviews");

        Page<PerformanceReviewResponse> page = performanceReviewService.getAllPerformanceReviews(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /performance-reviews/{id}} : Get performance review by ID.
     *
     * @param id the ID of the performance review to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the performance review
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
    public ResponseEntity<PerformanceReviewResponse> getPerformanceReview(@PathVariable UUID id) {
        LOG.debug("REST request to get PerformanceReview: {}", id);

        PerformanceReviewResponse result = performanceReviewService.getPerformanceReviewById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /performance-reviews/search} : Search performance reviews with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of performance reviews in body
     */
    @GetMapping("/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<PerformanceReviewResponse>> searchPerformanceReviews(
        PerformanceReviewSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search PerformanceReviews with criteria: {}", searchRequest);

        Page<PerformanceReviewResponse> page = performanceReviewService.searchPerformanceReviews(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /performance-reviews/{id}} : Delete performance review by ID.
     *
     * @param id the ID of the performance review to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deletePerformanceReview(@PathVariable UUID id) {
        LOG.debug("REST request to delete PerformanceReview: {}", id);

        performanceReviewService.deletePerformanceReview(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
