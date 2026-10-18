package com.humano.service.hr;

import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.EmployeePositionHistory;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.PositionChangeRequest;
import com.humano.dto.hr.responses.PositionChangeResponse;
import com.humano.dto.hr.responses.PositionChangeResponse.Ref;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.EmployeePositionHistoryRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes someone's position, department and/or pay from a date — a promotion, demotion, transfer
 * or correction — as one recorded step, so HR and payroll never disagree:
 * <ul>
 *   <li>the change is written to the position history ({@code APPLIED}), with its type and reason;</li>
 *   <li>the employee record moves to the new position / department;</li>
 *   <li>when the position or pay changes, the salary record is split on the date: the current one
 *       ends the day before, the new one carries the new position and amount — the record payroll
 *       pays each period from, and what a payslip shows as the position for its period.</li>
 * </ul>
 * The date can be today or earlier, but not inside payroll that is already approved or posted
 * (that pay would silently not change). Future dates come with scheduled changes, later.
 * <p>
 * Changing pay needs the compensation permission; the caller checks it.
 */
@Service
@Transactional("tenantTransactionManager")
public class PositionChangeService {

    private static final Logger log = LoggerFactory.getLogger(PositionChangeService.class);
    private static final String ENTITY_NAME = "positionChange";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeePositionHistoryRepository historyRepository;
    private final CompensationRepository compensationRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final AuditEventService auditEventService;

    public PositionChangeService(
        EmployeeRepository employeeRepository,
        PositionRepository positionRepository,
        DepartmentRepository departmentRepository,
        EmployeePositionHistoryRepository historyRepository,
        CompensationRepository compensationRepository,
        PayrollRunRepository payrollRunRepository,
        AuditEventService auditEventService
    ) {
        this.employeeRepository = employeeRepository;
        this.positionRepository = positionRepository;
        this.departmentRepository = departmentRepository;
        this.historyRepository = historyRepository;
        this.compensationRepository = compensationRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.auditEventService = auditEventService;
    }

    /** Applies the change and returns its history entry (with pay, for the one who made it). */
    public PositionChangeResponse change(UUID employeeId, PositionChangeRequest request) {
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
        LocalDate date = request.effectiveDate();
        checkDate(employee, date);

        Position newPosition = request.newPositionId() != null
            ? positionRepository
                .findById(request.newPositionId())
                .orElseThrow(() -> EntityNotFoundException.create("Position", request.newPositionId()))
            : null;
        Department newDepartment = request.newDepartmentId() != null
            ? departmentRepository
                .findById(request.newDepartmentId())
                .orElseThrow(() -> EntityNotFoundException.create("Department", request.newDepartmentId()))
            : null;
        boolean positionChanges = newPosition != null && !sameId(newPosition, employee.getPosition());
        boolean departmentChanges = newDepartment != null && !sameId(newDepartment, employee.getDepartment());

        Compensation current = activeSalary(employeeId, date);
        boolean salaryChanges =
            request.newSalary() != null && (current == null || current.getBaseAmount().compareTo(request.newSalary()) != 0);
        if (!positionChanges && !departmentChanges && !salaryChanges) {
            throw new BadRequestAlertException("Nothing changes: pick a new position, department or salary.", ENTITY_NAME, "nochange");
        }

        // The salary record follows the position and the pay: split it on the date.
        if (positionChanges || salaryChanges) {
            if (current == null) {
                if (salaryChanges) {
                    throw new BadRequestAlertException(
                        "This person has no salary record yet: set their pay on the Compensation tab first.",
                        ENTITY_NAME,
                        "nocompensation"
                    );
                }
            } else {
                splitSalary(
                    current,
                    date,
                    positionChanges ? newPosition : current.getPosition(),
                    salaryChanges ? request.newSalary() : null
                );
            }
        }

        EmployeePositionHistory history = new EmployeePositionHistory();
        history.setEmployee(employee);
        history.setChangeType(request.changeType());
        history.setStatus(PositionChangeStatus.APPLIED);
        history.setEffectiveDate(date);
        history.setReason(request.reason().trim());
        history.setOldPosition(employee.getPosition());
        history.setOldUnit(employee.getUnit());
        history.setOldManager(employee.getManager());
        if (positionChanges) {
            history.setNewPosition(newPosition);
        }
        if (departmentChanges) {
            history.setOldDepartment(employee.getDepartment());
            history.setNewDepartment(newDepartment);
        }
        if (salaryChanges) {
            history.setOldSalary(current != null ? current.getBaseAmount() : null);
            history.setNewSalary(request.newSalary());
        }
        history = historyRepository.save(history);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("employeeId", employeeId.toString());
        audit.put("changeType", request.changeType().name());
        audit.put("effectiveDate", date.toString());
        audit.put("reason", history.getReason());
        if (positionChanges) {
            audit.put("positionFrom", name(employee.getPosition()));
            audit.put("positionTo", newPosition.getName());
            // The job title follows the position when it was the position's name.
            if (employee.getPosition() != null && Objects.equals(employee.getJobTitle(), employee.getPosition().getName())) {
                employee.setJobTitle(newPosition.getName());
            }
            employee.setPosition(newPosition);
        }
        if (departmentChanges) {
            audit.put("departmentFrom", employee.getDepartment() != null ? employee.getDepartment().getName() : null);
            audit.put("departmentTo", newDepartment.getName());
            employee.setDepartment(newDepartment);
        }
        if (salaryChanges) {
            audit.put("salaryFrom", history.getOldSalary());
            audit.put("salaryTo", history.getNewSalary());
        }
        employeeRepository.save(employee);
        auditEventService.record("POSITION_CHANGED", "Employee", employeeId.toString(), audit);
        log.info("{} for employee {} effective {}", request.changeType(), employeeId, date);

        return toResponse(history, true);
    }

    /** Someone's changes, newest first; pay only when {@code withSalary}. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<PositionChangeResponse> history(UUID employeeId, boolean withSalary) {
        return historyRepository
            .findByEmployeeIdOrderByEffectiveDateDescCreatedDateDesc(employeeId)
            .stream()
            .map(h -> toResponse(h, withSalary))
            .toList();
    }

    /** Today or earlier, from the hire date, and after payroll that is already approved or posted. */
    private void checkDate(Employee employee, LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestAlertException(
                "The change can't take effect in the future yet: enter it on or after " + DATE.format(date) + ".",
                ENTITY_NAME,
                "futuredate"
            );
        }
        if (employee.getStartDate() != null && date.isBefore(employee.getStartDate())) {
            throw new BadRequestAlertException(
                "The change can't take effect before " + DATE.format(employee.getStartDate()) + ", the hire date.",
                ENTITY_NAME,
                "beforestart"
            );
        }
        LocalDate settled = payrollRunRepository.findLatestPeriodEndWithStatusIn(List.of(RunStatus.APPROVED, RunStatus.POSTED));
        if (settled != null && !date.isAfter(settled)) {
            throw new BadRequestAlertException(
                "Payroll up to " + DATE.format(settled) + " is already approved or posted: the change must take effect after it.",
                ENTITY_NAME,
                "payrollsettled"
            );
        }
    }

    /** The salary record in effect on {@code date} (latest-starting wins, as in payroll). */
    private Compensation activeSalary(UUID employeeId, LocalDate date) {
        return compensationRepository
            .findAllByEmployeeIds(List.of(employeeId))
            .stream()
            .filter(c -> c.getEffectiveFrom() != null && !c.getEffectiveFrom().isAfter(date))
            .filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(date))
            .max(Comparator.comparing(Compensation::getEffectiveFrom).thenComparing(Compensation::getId, Comparator.reverseOrder()))
            .orElse(null);
    }

    /**
     * Ends {@code current} the day before {@code date} and starts a copy on it with {@code position}
     * and, when given, the new amount. Refused when a record already starts on or after the date —
     * splitting there would leave a record ending before it starts, or overlap a later change.
     */
    private void splitSalary(Compensation current, LocalDate date, Position position, BigDecimal newAmount) {
        if (!current.getEffectiveFrom().isBefore(date)) {
            throw new BadRequestAlertException(
                "A salary record already starts on " + DATE.format(current.getEffectiveFrom()) + ": the change must take effect after it.",
                ENTITY_NAME,
                "salarysamedate"
            );
        }
        UUID employeeId = current.getEmployee().getId();
        boolean later = compensationRepository
            .findAllByEmployeeIds(List.of(employeeId))
            .stream()
            .anyMatch(c -> c.getEffectiveFrom() != null && c.getEffectiveFrom().isAfter(date));
        if (later) {
            throw new BadRequestAlertException(
                "A later salary change is already recorded: the change must take effect after it.",
                ENTITY_NAME,
                "latersalary"
            );
        }
        LocalDate end = current.getEffectiveTo();
        current.setEffectiveTo(date.minusDays(1));
        compensationRepository.save(current);

        Compensation next = new Compensation();
        next.setEmployee(current.getEmployee());
        next.setPosition(position);
        next.setBaseAmount(newAmount != null ? newAmount : current.getBaseAmount());
        next.setBasis(current.getBasis());
        next.setCurrency(current.getCurrency());
        next.setEffectiveFrom(date);
        next.setEffectiveTo(end);
        compensationRepository.save(next);
    }

    private PositionChangeResponse toResponse(EmployeePositionHistory h, boolean withSalary) {
        return new PositionChangeResponse(
            h.getId(),
            h.getChangeType(),
            h.getStatus(),
            h.getEffectiveDate(),
            h.getReason(),
            ref(h.getOldPosition()),
            ref(h.getNewPosition()),
            ref(h.getOldDepartment()),
            ref(h.getNewDepartment()),
            ref(h.getOldUnit()),
            ref(h.getNewUnit()),
            ref(h.getOldManager()),
            ref(h.getNewManager()),
            withSalary ? h.getOldSalary() : null,
            withSalary ? h.getNewSalary() : null,
            h.getCreatedBy(),
            h.getCreatedDate()
        );
    }

    private static Ref ref(Position p) {
        return p != null ? new Ref(p.getId(), p.getName()) : null;
    }

    private static Ref ref(Department d) {
        return d != null ? new Ref(d.getId(), d.getName()) : null;
    }

    private static Ref ref(OrganizationalUnit u) {
        return u != null ? new Ref(u.getId(), u.getName()) : null;
    }

    private static Ref ref(Employee e) {
        return e != null ? new Ref(e.getId(), e.getFirstName() + " " + e.getLastName()) : null;
    }

    private static String name(Position p) {
        return p != null ? p.getName() : null;
    }

    private static boolean sameId(Position a, Position b) {
        return b != null && Objects.equals(a.getId(), b.getId());
    }

    private static boolean sameId(Department a, Department b) {
        return b != null && Objects.equals(a.getId(), b.getId());
    }
}
