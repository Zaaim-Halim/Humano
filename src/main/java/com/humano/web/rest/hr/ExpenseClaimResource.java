package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateExpenseClaimRequest;
import com.humano.dto.hr.requests.ExpenseClaimSearchRequest;
import com.humano.dto.hr.requests.ProcessExpenseClaimRequest;
import com.humano.dto.hr.responses.ExpenseClaimResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.ExpenseClaimService;
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
 * REST controller for managing expense claims.
 */
@RestController
@RequestMapping("/api/hr/expense-claims")
public class ExpenseClaimResource {

    private static final Logger LOG = LoggerFactory.getLogger(ExpenseClaimResource.class);
    private static final String ENTITY_NAME = "expenseClaim";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExpenseClaimService expenseClaimService;

    public ExpenseClaimResource(ExpenseClaimService expenseClaimService) {
        this.expenseClaimService = expenseClaimService;
    }

    /**
     * {@code POST  /expense-claims} : Create a new expense claim.
     *
     * @param request the expense claim creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new expense claim
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
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<ExpenseClaimResponse> createExpenseClaim(@Valid @RequestBody CreateExpenseClaimRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create ExpenseClaim: {}", request);

        ExpenseClaimResponse result = expenseClaimService.createExpenseClaim(request);

        return ResponseEntity.created(new URI("/api/hr/expense-claims/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /expense-claims/{id}/process} : Process an existing expense claim (approve/reject).
     *
     * @param id the ID of the expense claim to process
     * @param request the expense claim process request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated expense claim
     */
    @PutMapping("/{id}/process")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<ExpenseClaimResponse> processExpenseClaim(
        @PathVariable UUID id,
        @Valid @RequestBody ProcessExpenseClaimRequest request
    ) {
        LOG.debug("REST request to process ExpenseClaim: {}", id);

        ExpenseClaimResponse result = expenseClaimService.processExpenseClaim(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /expense-claims} : Get all expense claims with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of expense claims in body
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
    public ResponseEntity<Page<ExpenseClaimResponse>> getAllExpenseClaims(Pageable pageable) {
        LOG.debug("REST request to get all ExpenseClaims");

        Page<ExpenseClaimResponse> page = expenseClaimService.getAllExpenseClaims(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /expense-claims/{id}} : Get expense claim by ID.
     *
     * @param id the ID of the expense claim to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the expense claim
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
    public ResponseEntity<ExpenseClaimResponse> getExpenseClaim(@PathVariable UUID id) {
        LOG.debug("REST request to get ExpenseClaim: {}", id);

        ExpenseClaimResponse result = expenseClaimService.getExpenseClaimById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /expense-claims/search} : Search expense claims with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of expense claims in body
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
    public ResponseEntity<Page<ExpenseClaimResponse>> searchExpenseClaims(ExpenseClaimSearchRequest searchRequest, Pageable pageable) {
        LOG.debug("REST request to search ExpenseClaims with criteria: {}", searchRequest);

        Page<ExpenseClaimResponse> page = expenseClaimService.searchExpenseClaims(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /expense-claims/employee/{employeeId}/search} : Search expense claims for a specific employee with criteria.
     *
     * @param employeeId the ID of the employee
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of expense claims in body
     */
    @GetMapping("/employee/{employeeId}/search")
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
    public ResponseEntity<Page<ExpenseClaimResponse>> searchExpenseClaimsByEmployee(
        @PathVariable UUID employeeId,
        ExpenseClaimSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search ExpenseClaims for employee {} with criteria: {}", employeeId, searchRequest);

        Page<ExpenseClaimResponse> page = expenseClaimService.searchExpenseClaimsByEmployee(employeeId, searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /expense-claims/{id}} : Delete expense claim by ID.
     *
     * @param id the ID of the expense claim to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteExpenseClaim(@PathVariable UUID id) {
        LOG.debug("REST request to delete ExpenseClaim: {}", id);

        expenseClaimService.deleteExpenseClaim(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
