package com.humano.service.hr;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.ExpenseClaim;
import com.humano.dto.hr.requests.CreateExpenseClaimRequest;
import com.humano.dto.hr.requests.ExpenseClaimSearchRequest;
import com.humano.dto.hr.requests.ProcessExpenseClaimRequest;
import com.humano.dto.hr.responses.ExpenseClaimResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.ExpenseClaimRepository;
import com.humano.repository.hr.specification.ExpenseClaimSpecification;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing expense claims.
 * Handles CRUD operations and processing for expense claim entities.
 */
@Service
public class ExpenseClaimService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseClaimService.class);
    private static final String ENTITY_NAME = "expenseClaim";

    private final ExpenseClaimRepository expenseClaimRepository;
    private final EmployeeRepository employeeRepository;

    public ExpenseClaimService(ExpenseClaimRepository expenseClaimRepository, EmployeeRepository employeeRepository) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new expense claim.
     *
     * @param request the expense claim creation request
     * @return the created expense claim response
     */
    @Transactional
    public ExpenseClaimResponse createExpenseClaim(CreateExpenseClaimRequest request) {
        log.debug("Request to create ExpenseClaim: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        ExpenseClaim claim = new ExpenseClaim();
        claim.setEmployee(employee);
        claim.setClaimDate(request.claimDate());
        claim.setAmount(request.amount());
        claim.setDescription(request.description());
        claim.setStatus(ExpenseClaimStatus.PENDING);

        ExpenseClaim savedClaim = expenseClaimRepository.save(claim);
        log.info("Created expense claim with ID: {}", savedClaim.getId());

        return mapToResponse(savedClaim);
    }

    /**
     * Process an expense claim (approve or reject).
     *
     * @param id the ID of the expense claim to process
     * @param request the processing request
     * @return the processed expense claim response
     */
    @Transactional
    public ExpenseClaimResponse processExpenseClaim(UUID id, ProcessExpenseClaimRequest request) {
        log.debug("Request to process ExpenseClaim: {} with status: {}", id, request.status());

        return expenseClaimRepository
            .findById(id)
            .map(claim -> {
                if (claim.getStatus() != ExpenseClaimStatus.PENDING) {
                    throw new BadRequestAlertException("Expense claim has already been processed", ENTITY_NAME, "alreadyprocessed");
                }

                claim.setStatus(request.status());
                return mapToResponse(expenseClaimRepository.save(claim));
            })
            .orElseThrow(() -> EntityNotFoundException.create("ExpenseClaim", id));
    }

    /**
     * Get an expense claim by ID.
     *
     * @param id the ID of the expense claim
     * @return the expense claim response
     */
    @Transactional(readOnly = true)
    public ExpenseClaimResponse getExpenseClaimById(UUID id) {
        log.debug("Request to get ExpenseClaim by ID: {}", id);

        return expenseClaimRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("ExpenseClaim", id));
    }

    /**
     * Get all expense claims with pagination.
     *
     * @param pageable pagination information
     * @return page of expense claim responses
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getAllExpenseClaims(Pageable pageable) {
        log.debug("Request to get all ExpenseClaims");

        return expenseClaimRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get expense claims by employee.
     *
     * @param employeeId the employee ID
     * @param pageable pagination information
     * @return page of expense claim responses
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getExpenseClaimsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get ExpenseClaims by Employee: {}", employeeId);

        return expenseClaimRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    /**
     * Get expense claims by status.
     *
     * @param status the expense claim status
     * @param pageable pagination information
     * @return page of expense claim responses
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getExpenseClaimsByStatus(ExpenseClaimStatus status, Pageable pageable) {
        log.debug("Request to get ExpenseClaims by Status: {}", status);

        return expenseClaimRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    /**
     * Get pending expense claims.
     *
     * @param pageable pagination information
     * @return page of expense claim responses
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getPendingExpenseClaims(Pageable pageable) {
        return getExpenseClaimsByStatus(ExpenseClaimStatus.PENDING, pageable);
    }

    /**
     * Get expense claims by employee and date range.
     *
     * @param employeeId the employee ID
     * @param startDate the start date
     * @param endDate the end date
     * @param pageable pagination information
     * @return page of expense claim responses
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getExpenseClaimsByEmployeeAndDateRange(
        UUID employeeId,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable
    ) {
        log.debug("Request to get ExpenseClaims by Employee: {} and date range: {} - {}", employeeId, startDate, endDate);

        return expenseClaimRepository
            .findByEmployeeIdAndClaimDateBetween(employeeId, startDate, endDate, pageable)
            .map(this::mapToResponse);
    }

    /**
     * Delete an expense claim by ID.
     *
     * @param id the ID of the expense claim to delete
     */
    @Transactional
    public void deleteExpenseClaim(UUID id) {
        log.debug("Request to delete ExpenseClaim: {}", id);

        if (!expenseClaimRepository.existsById(id)) {
            throw EntityNotFoundException.create("ExpenseClaim", id);
        }
        expenseClaimRepository.deleteById(id);
        log.info("Deleted expense claim with ID: {}", id);
    }

    /**
     * Search expense claims using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of expense claim responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> searchExpenseClaims(ExpenseClaimSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search ExpenseClaims with criteria: {}", searchRequest);

        Specification<ExpenseClaim> specification = ExpenseClaimSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.status(),
            searchRequest.claimDateFrom(),
            searchRequest.claimDateTo(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.description(),
            searchRequest.hasReceipt(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return expenseClaimRepository.findAll(specification, pageable).map(this::mapToResponse);
    }

    /**
     * Search expense claims for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of expense claim responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> searchExpenseClaimsByEmployee(
        UUID employeeId,
        ExpenseClaimSearchRequest searchRequest,
        Pageable pageable
    ) {
        log.debug("Request to search ExpenseClaims for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        ExpenseClaimSearchRequest modifiedRequest = new ExpenseClaimSearchRequest(
            employeeId,
            searchRequest.status(),
            searchRequest.claimDateFrom(),
            searchRequest.claimDateTo(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.description(),
            searchRequest.hasReceipt(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return searchExpenseClaims(modifiedRequest, pageable);
    }

    private ExpenseClaimResponse mapToResponse(ExpenseClaim claim) {
        String employeeName = claim.getEmployee().getFirstName() + " " + claim.getEmployee().getLastName();

        return new ExpenseClaimResponse(
            claim.getId(),
            claim.getEmployee().getId(),
            employeeName,
            claim.getClaimDate(),
            claim.getAmount(),
            claim.getDescription(),
            claim.getStatus(),
            claim.getReceiptUrl(),
            claim.getCreatedBy(),
            claim.getCreatedDate(),
            claim.getLastModifiedBy(),
            claim.getLastModifiedDate()
        );
    }
}
