package com.humano.service.hr;

import com.humano.domain.hr.Employee;
import com.humano.domain.hr.Project;
import com.humano.domain.hr.Timesheet;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.ProjectRepository;
import com.humano.repository.hr.TimesheetRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreateTimesheetRequest;
import com.humano.service.hr.dto.requests.UpdateTimesheetRequest;
import com.humano.service.hr.dto.responses.TimesheetResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimesheetService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetService.class);

    private final TimesheetRepository timesheetRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;

    public TimesheetService(
        TimesheetRepository timesheetRepository,
        EmployeeRepository employeeRepository,
        ProjectRepository projectRepository
    ) {
        this.timesheetRepository = timesheetRepository;
        this.employeeRepository = employeeRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TimesheetResponse createTimesheet(CreateTimesheetRequest request) {
        log.debug("Request to create Timesheet: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        Timesheet timesheet = new Timesheet();
        timesheet.setEmployee(employee);
        timesheet.setDate(request.date());
        timesheet.setHoursWorked(request.hoursWorked());

        if (request.projectId() != null) {
            Project project = projectRepository
                .findById(request.projectId())
                .orElseThrow(() -> EntityNotFoundException.create("Project", request.projectId()));
            timesheet.setProject(project);
        }

        Timesheet savedTimesheet = timesheetRepository.save(timesheet);
        log.info("Created timesheet with ID: {}", savedTimesheet.getId());

        return mapToResponse(savedTimesheet);
    }

    @Transactional
    public TimesheetResponse updateTimesheet(UUID id, UpdateTimesheetRequest request) {
        log.debug("Request to update Timesheet: {}", id);

        return timesheetRepository
            .findById(id)
            .map(timesheet -> {
                if (request.date() != null) {
                    timesheet.setDate(request.date());
                }
                if (request.hoursWorked() != null) {
                    timesheet.setHoursWorked(request.hoursWorked());
                }
                if (request.projectId() != null) {
                    Project project = projectRepository
                        .findById(request.projectId())
                        .orElseThrow(() -> EntityNotFoundException.create("Project", request.projectId()));
                    timesheet.setProject(project);
                }
                return mapToResponse(timesheetRepository.save(timesheet));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Timesheet", id));
    }

    @Transactional(readOnly = true)
    public TimesheetResponse getTimesheetById(UUID id) {
        log.debug("Request to get Timesheet by ID: {}", id);

        return timesheetRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Timesheet", id));
    }

    @Transactional(readOnly = true)
    public Page<TimesheetResponse> getAllTimesheets(Pageable pageable) {
        log.debug("Request to get all Timesheets");

        return timesheetRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<TimesheetResponse> getTimesheetsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get Timesheets by Employee: {}", employeeId);

        return timesheetRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<TimesheetResponse> getTimesheetsByProject(UUID projectId, Pageable pageable) {
        log.debug("Request to get Timesheets by Project: {}", projectId);

        return timesheetRepository.findByProjectId(projectId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<TimesheetResponse> getTimesheetsByEmployeeAndDateRange(
        UUID employeeId,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable
    ) {
        log.debug("Request to get Timesheets by Employee: {} and date range: {} - {}", employeeId, startDate, endDate);

        return timesheetRepository.findByEmployeeIdAndDateBetween(employeeId, startDate, endDate, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalHoursWorked(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        log.debug("Request to get total hours worked by Employee: {} in date range: {} - {}", employeeId, startDate, endDate);

        return timesheetRepository.sumHoursWorkedByEmployeeIdAndDateBetween(employeeId, startDate, endDate);
    }

    @Transactional
    public void deleteTimesheet(UUID id) {
        log.debug("Request to delete Timesheet: {}", id);

        if (!timesheetRepository.existsById(id)) {
            throw EntityNotFoundException.create("Timesheet", id);
        }
        timesheetRepository.deleteById(id);
        log.info("Deleted timesheet with ID: {}", id);
    }

    private TimesheetResponse mapToResponse(Timesheet timesheet) {
        String employeeName = timesheet.getEmployee().getFirstName() + " " + timesheet.getEmployee().getLastName();

        return new TimesheetResponse(
            timesheet.getId(),
            timesheet.getEmployee().getId(),
            employeeName,
            timesheet.getDate(),
            timesheet.getHoursWorked(),
            timesheet.getProject() != null ? timesheet.getProject().getId() : null,
            timesheet.getProject() != null ? timesheet.getProject().getName() : null,
            timesheet.getCreatedBy(),
            timesheet.getCreatedDate(),
            timesheet.getLastModifiedBy(),
            timesheet.getLastModifiedDate()
        );
    }
}
