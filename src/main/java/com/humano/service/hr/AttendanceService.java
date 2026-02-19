package com.humano.service.hr;

import com.humano.domain.hr.Attendance;
import com.humano.domain.hr.AttendanceEvent;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.AttendanceEventSearchRequest;
import com.humano.dto.hr.requests.AttendanceSearchRequest;
import com.humano.dto.hr.requests.CreateAttendanceEventRequest;
import com.humano.dto.hr.requests.CreateAttendanceRequest;
import com.humano.dto.hr.requests.UpdateAttendanceRequest;
import com.humano.dto.hr.responses.AttendanceEventResponse;
import com.humano.dto.hr.responses.AttendanceResponse;
import com.humano.repository.hr.AttendanceEventRepository;
import com.humano.repository.hr.AttendanceRepository;
import com.humano.repository.hr.specification.AttendanceEventSpecification;
import com.humano.repository.hr.specification.AttendanceSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing attendance records.
 * Handles CRUD operations for attendance and attendance events.
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(
        AttendanceRepository attendanceRepository,
        AttendanceEventRepository attendanceEventRepository,
        EmployeeRepository employeeRepository
    ) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceEventRepository = attendanceEventRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new attendance record.
     *
     * @param request the attendance creation request
     * @return the created attendance response
     */
    @Transactional
    public AttendanceResponse createAttendance(CreateAttendanceRequest request) {
        log.debug("Request to create Attendance: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setDate(request.date());
        attendance.setCheckIn(request.checkIn());
        attendance.setCheckOut(request.checkOut());
        attendance.setStatus(request.status());

        Attendance savedAttendance = attendanceRepository.save(attendance);
        log.info("Created attendance with ID: {}", savedAttendance.getId());

        return mapToResponse(savedAttendance);
    }

    /**
     * Update an existing attendance record.
     *
     * @param id the ID of the attendance to update
     * @param request the attendance update request
     * @return the updated attendance response
     */
    @Transactional
    public AttendanceResponse updateAttendance(UUID id, UpdateAttendanceRequest request) {
        log.debug("Request to update Attendance: {}", id);

        return attendanceRepository
            .findById(id)
            .map(attendance -> {
                if (request.checkIn() != null) {
                    attendance.setCheckIn(request.checkIn());
                }
                if (request.checkOut() != null) {
                    attendance.setCheckOut(request.checkOut());
                }
                if (request.status() != null) {
                    attendance.setStatus(request.status());
                }
                return mapToResponse(attendanceRepository.save(attendance));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Attendance", id));
    }

    /**
     * Get an attendance record by ID.
     *
     * @param id the ID of the attendance
     * @return the attendance response
     */
    @Transactional(readOnly = true)
    public AttendanceResponse getAttendanceById(UUID id) {
        log.debug("Request to get Attendance by ID: {}", id);

        return attendanceRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Attendance", id));
    }

    /**
     * Get all attendance records with pagination.
     *
     * @param pageable pagination information
     * @return page of attendance responses
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAllAttendance(Pageable pageable) {
        log.debug("Request to get all Attendance records");

        return attendanceRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get attendance records by employee.
     *
     * @param employeeId the employee ID
     * @param pageable pagination information
     * @return page of attendance responses
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAttendanceByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get Attendance by Employee: {}", employeeId);

        return attendanceRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    /**
     * Get attendance records by employee and date range.
     *
     * @param employeeId the employee ID
     * @param startDate the start date
     * @param endDate the end date
     * @param pageable pagination information
     * @return page of attendance responses
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAttendanceByEmployeeAndDateRange(
        UUID employeeId,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable
    ) {
        log.debug("Request to get Attendance by Employee: {} and date range: {} - {}", employeeId, startDate, endDate);

        return attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, startDate, endDate, pageable).map(this::mapToResponse);
    }

    /**
     * Add an attendance event.
     *
     * @param request the attendance event creation request
     * @return the updated attendance response
     */
    @Transactional
    public AttendanceResponse addAttendanceEvent(CreateAttendanceEventRequest request) {
        log.debug("Request to add AttendanceEvent: {}", request);

        Attendance attendance = attendanceRepository
            .findById(request.attendanceId())
            .orElseThrow(() -> EntityNotFoundException.create("Attendance", request.attendanceId()));

        AttendanceEvent event = new AttendanceEvent();
        event.setAttendance(attendance);
        event.setEventType(request.eventType());
        event.setEventTime(request.eventTime());
        event.setEventAction(request.eventAction());
        event.setDescription(request.description());

        attendanceEventRepository.save(event);
        log.info("Added attendance event with ID: {}", event.getId());

        return mapToResponse(attendanceRepository.findById(request.attendanceId()).orElseThrow());
    }

    /**
     * Delete an attendance record by ID.
     *
     * @param id the ID of the attendance to delete
     */
    @Transactional
    public void deleteAttendance(UUID id) {
        log.debug("Request to delete Attendance: {}", id);

        if (!attendanceRepository.existsById(id)) {
            throw EntityNotFoundException.create("Attendance", id);
        }
        attendanceRepository.deleteById(id);
        log.info("Deleted attendance with ID: {}", id);
    }

    /**
     * Search attendance records using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of attendance responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> searchAttendance(AttendanceSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Attendance with criteria: {}", searchRequest);

        Specification<Attendance> specification = AttendanceSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.startDate(),
            searchRequest.endDate(),
            searchRequest.status(),
            searchRequest.checkInFrom(),
            searchRequest.checkInTo(),
            searchRequest.checkOutFrom(),
            searchRequest.checkOutTo(),
            searchRequest.createdBy(),
            searchRequest.lastModifiedBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return attendanceRepository.findAll(specification, pageable).map(this::mapToResponse);
    }

    /**
     * Search attendance records for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of attendance responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> searchAttendanceByEmployee(UUID employeeId, AttendanceSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Attendance for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        AttendanceSearchRequest modifiedRequest = new AttendanceSearchRequest(
            employeeId,
            searchRequest.startDate(),
            searchRequest.endDate(),
            searchRequest.status(),
            searchRequest.checkInFrom(),
            searchRequest.checkInTo(),
            searchRequest.checkOutFrom(),
            searchRequest.checkOutTo(),
            searchRequest.createdBy(),
            searchRequest.lastModifiedBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return searchAttendance(modifiedRequest, pageable);
    }

    /**
     * Search attendance events using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of attendance event responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<AttendanceEventResponse> searchAttendanceEvents(AttendanceEventSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search AttendanceEvents with criteria: {}", searchRequest);

        Specification<AttendanceEvent> specification = AttendanceEventSpecification.withCriteria(
            searchRequest.attendanceId(),
            searchRequest.employeeId(),
            searchRequest.eventType(),
            searchRequest.eventAction(),
            searchRequest.eventTimeFrom(),
            searchRequest.eventTimeTo(),
            searchRequest.description(),
            searchRequest.createdBy(),
            searchRequest.lastModifiedBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return attendanceEventRepository.findAll(specification, pageable).map(this::mapToAttendanceEventResponse);
    }

    /**
     * Search attendance events for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of attendance event responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<AttendanceEventResponse> searchAttendanceEventsByEmployee(
        UUID employeeId,
        AttendanceEventSearchRequest searchRequest,
        Pageable pageable
    ) {
        log.debug("Request to search AttendanceEvents for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        AttendanceEventSearchRequest modifiedRequest = new AttendanceEventSearchRequest(
            searchRequest.attendanceId(),
            employeeId,
            searchRequest.eventType(),
            searchRequest.eventAction(),
            searchRequest.eventTimeFrom(),
            searchRequest.eventTimeTo(),
            searchRequest.description(),
            searchRequest.createdBy(),
            searchRequest.lastModifiedBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return searchAttendanceEvents(modifiedRequest, pageable);
    }

    private AttendanceResponse mapToResponse(Attendance attendance) {
        String employeeName = attendance.getEmployee().getFirstName() + " " + attendance.getEmployee().getLastName();

        List<AttendanceEventResponse> events = Collections.emptyList();
        if (attendance.getEvents() != null && !attendance.getEvents().isEmpty()) {
            events = attendance
                .getEvents()
                .stream()
                .map(event ->
                    new AttendanceEventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getEventTime(),
                        event.getEventAction(),
                        event.getDescription()
                    )
                )
                .collect(Collectors.toList());
        }

        return new AttendanceResponse(
            attendance.getId(),
            attendance.getEmployee().getId(),
            employeeName,
            attendance.getDate(),
            attendance.getCheckIn(),
            attendance.getCheckOut(),
            attendance.getStatus(),
            events,
            attendance.getCreatedBy(),
            attendance.getCreatedDate(),
            attendance.getLastModifiedBy(),
            attendance.getLastModifiedDate()
        );
    }

    private AttendanceEventResponse mapToAttendanceEventResponse(AttendanceEvent event) {
        return new AttendanceEventResponse(
            event.getId(),
            event.getEventType(),
            event.getEventTime(),
            event.getEventAction(),
            event.getDescription()
        );
    }
}
