package com.humano.service.hr;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.OvertimeRecord;
import com.humano.dto.hr.requests.CreateOvertimeRecordRequest;
import com.humano.dto.hr.requests.ProcessOvertimeRecordRequest;
import com.humano.dto.hr.responses.OvertimeRecordResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.OvertimeRecordRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing overtime records.
 * Handles CRUD operations and approval processing for overtime record entities.
 */
@Service
public class OvertimeRecordService {

    private static final Logger log = LoggerFactory.getLogger(OvertimeRecordService.class);
    private static final String ENTITY_NAME = "overtimeRecord";

    private final OvertimeRecordRepository overtimeRecordRepository;
    private final EmployeeRepository employeeRepository;

    public OvertimeRecordService(OvertimeRecordRepository overtimeRecordRepository, EmployeeRepository employeeRepository) {
        this.overtimeRecordRepository = overtimeRecordRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new overtime record.
     *
     * @param request the overtime record creation request
     * @return the created overtime record response
     */
    @Transactional
    public OvertimeRecordResponse createOvertimeRecord(CreateOvertimeRecordRequest request) {
        log.debug("Request to create OvertimeRecord: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        OvertimeRecord record = new OvertimeRecord();
        record.setEmployee(employee);
        record.setDate(request.date());
        record.setHours(request.hours());
        record.setType(request.type());
        record.setNotes(request.notes());
        record.setApprovalStatus(OvertimeApprovalStatus.PENDING);

        OvertimeRecord savedRecord = overtimeRecordRepository.save(record);
        log.info("Created overtime record with ID: {}", savedRecord.getId());

        return mapToResponse(savedRecord);
    }

    /**
     * Process an overtime record (approve or reject).
     *
     * @param id the ID of the overtime record to process
     * @param request the processing request
     * @return the processed overtime record response
     */
    @Transactional
    public OvertimeRecordResponse processOvertimeRecord(UUID id, ProcessOvertimeRecordRequest request) {
        log.debug("Request to process OvertimeRecord: {} with status: {}", id, request.approvalStatus());

        return overtimeRecordRepository
            .findById(id)
            .map(record -> {
                if (record.getApprovalStatus() != OvertimeApprovalStatus.PENDING) {
                    throw new BadRequestAlertException("Overtime record has already been processed", ENTITY_NAME, "alreadyprocessed");
                }

                Employee approver = employeeRepository
                    .findById(request.approvedById())
                    .orElseThrow(() -> EntityNotFoundException.create("Employee", request.approvedById()));

                record.setApprovalStatus(request.approvalStatus());
                record.setApprovedBy(approver);
                if (request.notes() != null) {
                    record.setNotes(request.notes());
                }

                return mapToResponse(overtimeRecordRepository.save(record));
            })
            .orElseThrow(() -> EntityNotFoundException.create("OvertimeRecord", id));
    }

    /**
     * Get an overtime record by ID.
     *
     * @param id the ID of the overtime record
     * @return the overtime record response
     */
    @Transactional(readOnly = true)
    public OvertimeRecordResponse getOvertimeRecordById(UUID id) {
        log.debug("Request to get OvertimeRecord by ID: {}", id);

        return overtimeRecordRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("OvertimeRecord", id));
    }

    /**
     * Get all overtime records with pagination.
     *
     * @param pageable pagination information
     * @return page of overtime record responses
     */
    @Transactional(readOnly = true)
    public Page<OvertimeRecordResponse> getAllOvertimeRecords(Pageable pageable) {
        log.debug("Request to get all OvertimeRecords");

        return overtimeRecordRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get overtime records by employee.
     *
     * @param employeeId the employee ID
     * @param pageable pagination information
     * @return page of overtime record responses
     */
    @Transactional(readOnly = true)
    public Page<OvertimeRecordResponse> getOvertimeRecordsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get OvertimeRecords by Employee: {}", employeeId);

        return overtimeRecordRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    /**
     * Get overtime records by approval status.
     *
     * @param status the approval status
     * @param pageable pagination information
     * @return page of overtime record responses
     */
    @Transactional(readOnly = true)
    public Page<OvertimeRecordResponse> getOvertimeRecordsByStatus(OvertimeApprovalStatus status, Pageable pageable) {
        log.debug("Request to get OvertimeRecords by Status: {}", status);

        return overtimeRecordRepository.findByApprovalStatus(status, pageable).map(this::mapToResponse);
    }

    /**
     * Get pending overtime records.
     *
     * @param pageable pagination information
     * @return page of overtime record responses
     */
    @Transactional(readOnly = true)
    public Page<OvertimeRecordResponse> getPendingOvertimeRecords(Pageable pageable) {
        return getOvertimeRecordsByStatus(OvertimeApprovalStatus.PENDING, pageable);
    }

    /**
     * Get overtime records by employee and date range.
     *
     * @param employeeId the employee ID
     * @param startDate the start date
     * @param endDate the end date
     * @param pageable pagination information
     * @return page of overtime record responses
     */
    @Transactional(readOnly = true)
    public Page<OvertimeRecordResponse> getOvertimeRecordsByEmployeeAndDateRange(
        UUID employeeId,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable
    ) {
        log.debug("Request to get OvertimeRecords by Employee: {} and date range: {} - {}", employeeId, startDate, endDate);

        return overtimeRecordRepository.findByEmployeeIdAndDateBetween(employeeId, startDate, endDate, pageable).map(this::mapToResponse);
    }

    /**
     * Delete an overtime record by ID.
     *
     * @param id the ID of the overtime record to delete
     */
    @Transactional
    public void deleteOvertimeRecord(UUID id) {
        log.debug("Request to delete OvertimeRecord: {}", id);

        if (!overtimeRecordRepository.existsById(id)) {
            throw EntityNotFoundException.create("OvertimeRecord", id);
        }
        overtimeRecordRepository.deleteById(id);
        log.info("Deleted overtime record with ID: {}", id);
    }

    private OvertimeRecordResponse mapToResponse(OvertimeRecord record) {
        String employeeName = record.getEmployee().getFirstName() + " " + record.getEmployee().getLastName();
        String approvedByName = null;
        if (record.getApprovedBy() != null) {
            approvedByName = record.getApprovedBy().getFirstName() + " " + record.getApprovedBy().getLastName();
        }

        return new OvertimeRecordResponse(
            record.getId(),
            record.getEmployee().getId(),
            employeeName,
            record.getDate(),
            record.getHours(),
            record.getType(),
            record.getApprovalStatus(),
            record.getNotes(),
            record.getApprovedBy() != null ? record.getApprovedBy().getId() : null,
            approvedByName,
            record.getCreatedBy(),
            record.getCreatedDate(),
            record.getLastModifiedBy(),
            record.getLastModifiedDate()
        );
    }
}
