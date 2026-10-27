package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.CompetencyRequest;
import com.humano.dto.hr.workflow.requests.ReviewTemplateRequest;
import com.humano.dto.hr.workflow.responses.CompetencyResponse;
import com.humano.dto.hr.workflow.responses.ReviewTemplateResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.PerformanceFrameworkService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the performance framework ({@code /api/hr/performance-frameworks}): the
 * competencies reviewers rate and the review templates built from them. Read by anyone who sees or
 * sets up review cycles (the cycle form picks a template); changed with
 * {@code MANAGE_PERFORMANCE_FRAMEWORKS}.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RestController
@RequestMapping("/api/hr/performance-frameworks")
public class PerformanceFrameworkResource {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceFrameworkResource.class);
    private static final String READ =
        "@securityExpressions.hasAnyPermission('" +
        PermissionsConstants.VIEW_PERFORMANCE_REVIEWS +
        "', '" +
        PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES +
        "', '" +
        PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS +
        "')";

    private final PerformanceFrameworkService service;

    public PerformanceFrameworkResource(PerformanceFrameworkService service) {
        this.service = service;
    }

    /** {@code GET /competencies} : the framework, by category then name. */
    @GetMapping("/competencies")
    @PreAuthorize(READ)
    public ResponseEntity<List<CompetencyResponse>> listCompetencies() {
        return ResponseEntity.ok(service.listCompetencies());
    }

    /** {@code POST /competencies} : add a competency. */
    @PostMapping("/competencies")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS)
    public ResponseEntity<CompetencyResponse> createCompetency(@Valid @RequestBody CompetencyRequest request) {
        LOG.debug("REST request to create competency: {}", request.name());
        return ResponseEntity.ok(service.createCompetency(request));
    }

    /** {@code PUT /competencies/:id} : change or retire a competency. */
    @PutMapping("/competencies/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS)
    public ResponseEntity<CompetencyResponse> updateCompetency(@PathVariable UUID id, @Valid @RequestBody CompetencyRequest request) {
        LOG.debug("REST request to update competency: {}", id);
        return ResponseEntity.ok(service.updateCompetency(id, request));
    }

    /** {@code DELETE /competencies/:id} : delete a competency no template uses. */
    @DeleteMapping("/competencies/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS)
    public ResponseEntity<Void> deleteCompetency(@PathVariable UUID id) {
        LOG.debug("REST request to delete competency: {}", id);
        service.deleteCompetency(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /templates} : the review templates, by name. */
    @GetMapping("/templates")
    @PreAuthorize(READ)
    public ResponseEntity<List<ReviewTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(service.listTemplates());
    }

    /** {@code GET /templates/:id} : one review template. */
    @GetMapping("/templates/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ReviewTemplateResponse> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getTemplate(id));
    }

    /** {@code POST /templates} : add a review template. */
    @PostMapping("/templates")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS)
    public ResponseEntity<ReviewTemplateResponse> createTemplate(@Valid @RequestBody ReviewTemplateRequest request) {
        LOG.debug("REST request to create review template: {}", request.name());
        return ResponseEntity.ok(service.createTemplate(request));
    }

    /** {@code PUT /templates/:id} : change or retire a review template. Opened cycles keep their copy. */
    @PutMapping("/templates/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS)
    public ResponseEntity<ReviewTemplateResponse> updateTemplate(@PathVariable UUID id, @Valid @RequestBody ReviewTemplateRequest request) {
        LOG.debug("REST request to update review template: {}", id);
        return ResponseEntity.ok(service.updateTemplate(id, request));
    }

    /** {@code DELETE /templates/:id} : delete a review template no cycle uses. */
    @DeleteMapping("/templates/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS)
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        LOG.debug("REST request to delete review template: {}", id);
        service.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
