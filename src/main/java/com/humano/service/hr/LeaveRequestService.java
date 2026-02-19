package com.humano.service.hr;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.hr.LeaveRequest;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateLeaveRequest;
import com.humano.dto.hr.requests.LeaveRequestSearchRequest;
import com.humano.dto.hr.requests.ProcessLeaveRequest;
import com.humano.dto.hr.responses.LeaveRequestResponse;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.specification.LeaveRequestSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);
    private static final String ENTITY_NAME = "leaveRequest";

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, EmployeeRepository employeeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public LeaveRequestResponse createLeaveRequest(CreateLeaveRequest request) {
        log.debug("Request to create LeaveRequest: {}", request);

        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestAlertException("End date cannot be before start date", ENTITY_NAME, "invaliddates");
        }

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setStartDate(request.startDate());
        leaveRequest.setEndDate(request.endDate());
        leaveRequest.setLeaveType(request.leaveType());
        leaveRequest.setReason(request.reason());
        leaveRequest.setStatus(LeaveStatus.PENDING);

        long daysCount = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        leaveRequest.setDaysCount((int) daysCount);

        LeaveRequest savedRequest = leaveRequestRepository.save(leaveRequest);
        log.info("Created leave request with ID: {}", savedRequest.getId());

        return mapToResponse(savedRequest);
    }

    @Transactional
    public LeaveRequestResponse processLeaveRequest(UUID id, ProcessLeaveRequest request) {
        log.debug("Request to process LeaveRequest: {} with status: {}", id, request.status());

        return leaveRequestRepository
            .findById(id)
            .map(leaveRequest -> {
                if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
                    throw new BadRequestAlertException("Leave request has already been processed", ENTITY_NAME, "alreadyprocessed");
                }

                Employee approver = employeeRepository
                    .findById(request.approverId())
                    .orElseThrow(() -> EntityNotFoundException.create("Employee", request.approverId()));

                leaveRequest.setStatus(request.status());
                leaveRequest.setApprover(approver);
                leaveRequest.setApproverComments(request.approverComments());

                return mapToResponse(leaveRequestRepository.save(leaveRequest));
            })
            .orElseThrow(() -> EntityNotFoundException.create("LeaveRequest", id));
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getLeaveRequestById(UUID id) {
        log.debug("Request to get LeaveRequest by ID: {}", id);

        return leaveRequestRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("LeaveRequest", id));
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getAllLeaveRequests(Pageable pageable) {
        log.debug("Request to get all LeaveRequests");

        return leaveRequestRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getLeaveRequestsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get LeaveRequests by Employee: {}", employeeId);

        return leaveRequestRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getLeaveRequestsByStatus(LeaveStatus status, Pageable pageable) {
        log.debug("Request to get LeaveRequests by Status: {}", status);

        return leaveRequestRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getPendingLeaveRequests(Pageable pageable) {
        return getLeaveRequestsByStatus(LeaveStatus.PENDING, pageable);
    }

    @Transactional
    public LeaveRequestResponse cancelLeaveRequest(UUID id) {
        log.debug("Request to cancel LeaveRequest: {}", id);

        return leaveRequestRepository
            .findById(id)
            .map(leaveRequest -> {
                if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
                    throw new BadRequestAlertException("Only pending leave requests can be cancelled", ENTITY_NAME, "cannotcancel");
                }

                leaveRequest.setStatus(LeaveStatus.CANCELED);
                return mapToResponse(leaveRequestRepository.save(leaveRequest));
            })
            .orElseThrow(() -> EntityNotFoundException.create("LeaveRequest", id));
    }

    @Transactional
    public void deleteLeaveRequest(UUID id) {
        log.debug("Request to delete LeaveRequest: {}", id);

        if (!leaveRequestRepository.existsById(id)) {
            throw EntityNotFoundException.create("LeaveRequest", id);
        }
        leaveRequestRepository.deleteById(id);
        log.info("Deleted leave request with ID: {}", id);
    }

    /**
     * Search leave requests using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of leave request responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> searchLeaveRequests(LeaveRequestSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search LeaveRequests with criteria: {}", searchRequest);

        Specification<LeaveRequest> specification = LeaveRequestSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.approverId(),
            searchRequest.leaveType(),
            searchRequest.status(),
            searchRequest.startDateFrom(),
            searchRequest.startDateTo(),
            searchRequest.endDateFrom(),
            searchRequest.endDateTo(),
            searchRequest.reason(),
            searchRequest.minDaysCount(),
            searchRequest.maxDaysCount(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return leaveRequestRepository.findAll(specification, pageable).map(this::mapToResponse);
    }

    /**
     * Search leave requests for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of leave request responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> searchLeaveRequestsByEmployee(
        UUID employeeId,
        LeaveRequestSearchRequest searchRequest,
        Pageable pageable
    ) {
        log.debug("Request to search LeaveRequests for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        LeaveRequestSearchRequest modifiedRequest = new LeaveRequestSearchRequest(
            employeeId,
            searchRequest.approverId(),
            searchRequest.leaveType(),
            searchRequest.status(),
            searchRequest.startDateFrom(),
            searchRequest.startDateTo(),
            searchRequest.endDateFrom(),
            searchRequest.endDateTo(),
            searchRequest.reason(),
            searchRequest.minDaysCount(),
            searchRequest.maxDaysCount(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return searchLeaveRequests(modifiedRequest, pageable);
    }

    private LeaveRequestResponse mapToResponse(LeaveRequest leaveRequest) {
        String employeeName = leaveRequest.getEmployee().getFirstName() + " " + leaveRequest.getEmployee().getLastName();
        String approverName = null;
        if (leaveRequest.getApprover() != null) {
            approverName = leaveRequest.getApprover().getFirstName() + " " + leaveRequest.getApprover().getLastName();
        }

        return new LeaveRequestResponse(
            leaveRequest.getId(),
            leaveRequest.getStartDate(),
            leaveRequest.getEndDate(),
            leaveRequest.getLeaveType(),
            leaveRequest.getStatus(),
            leaveRequest.getReason(),
            leaveRequest.getDaysCount(),
            leaveRequest.getEmployee().getId(),
            employeeName,
            leaveRequest.getApprover() != null ? leaveRequest.getApprover().getId() : null,
            approverName,
            leaveRequest.getApproverComments(),
            leaveRequest.getCreatedBy(),
            leaveRequest.getCreatedDate(),
            leaveRequest.getLastModifiedBy(),
            leaveRequest.getLastModifiedDate()
        );
    }
}
