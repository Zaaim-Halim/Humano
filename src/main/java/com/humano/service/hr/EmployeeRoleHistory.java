package com.humano.service.hr;

import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.EmployeePositionHistory;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.shared.Employee;
import com.humano.repository.hr.EmployeePositionHistoryRepository;
import com.humano.repository.payroll.CompensationRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Someone's position and department on a given date — what a payslip shows for its period, so a
 * later promotion or transfer never rewrites past payslips. Nothing is copied for this; it is read
 * from what is already dated:
 * <ul>
 *   <li>position: the salary record ({@code Compensation}) in effect on the date — the very record
 *       payroll paid that period from; else the position history; else the current position;</li>
 *   <li>department: the position history ({@code APPLIED} changes); else the current department.</li>
 * </ul>
 * Names are read as they are now: renaming a position renames it on past payslips too.
 * <p>
 * Built for lists: {@link #lookup} loads the records of all the employees in two queries.
 */
@Service
@Transactional(value = "tenantTransactionManager", readOnly = true)
public class EmployeeRoleHistory {

    /** Position and department names on a date; either may be null. */
    public record Role(String position, String department) {}

    private final CompensationRepository compensationRepository;
    private final EmployeePositionHistoryRepository historyRepository;

    public EmployeeRoleHistory(CompensationRepository compensationRepository, EmployeePositionHistoryRepository historyRepository) {
        this.compensationRepository = compensationRepository;
        this.historyRepository = historyRepository;
    }

    /** The dated records of these employees, ready for {@link Lookup#at} questions. */
    public Lookup lookup(Collection<Employee> employees) {
        List<UUID> ids = employees.stream().filter(Objects::nonNull).map(Employee::getId).distinct().toList();
        if (ids.isEmpty()) {
            return new Lookup(Map.of(), Map.of());
        }
        Map<UUID, List<Compensation>> salaries = compensationRepository
            .findAllByEmployeeIds(ids)
            .stream()
            .collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
        Map<UUID, List<EmployeePositionHistory>> changes = historyRepository
            .findByEmployeeIdInAndStatus(ids, PositionChangeStatus.APPLIED)
            .stream()
            .sorted(Comparator.comparing(EmployeePositionHistory::getEffectiveDate, Comparator.nullsFirst(Comparator.naturalOrder())))
            .collect(Collectors.groupingBy(h -> h.getEmployee().getId()));
        return new Lookup(salaries, changes);
    }

    /** One employee's role on one date. */
    public Role at(Employee employee, LocalDate date) {
        return lookup(List.of(employee)).at(employee, date);
    }

    public static final class Lookup {

        private final Map<UUID, List<Compensation>> salaries;
        private final Map<UUID, List<EmployeePositionHistory>> changes;

        private Lookup(Map<UUID, List<Compensation>> salaries, Map<UUID, List<EmployeePositionHistory>> changes) {
            this.salaries = salaries;
            this.changes = changes;
        }

        public Role at(Employee employee, LocalDate date) {
            if (employee == null) {
                return new Role(null, null);
            }
            List<EmployeePositionHistory> history = changes.getOrDefault(employee.getId(), List.of());
            Position position = positionOnSalary(employee.getId(), date);
            if (position == null) {
                position = asOf(
                    history,
                    date,
                    h -> h.getNewPosition() != null,
                    EmployeePositionHistory::getNewPosition,
                    EmployeePositionHistory::getOldPosition
                );
            }
            if (position == null) {
                position = employee.getPosition();
            }
            Department department = asOf(
                history,
                date,
                h -> h.getNewDepartment() != null,
                EmployeePositionHistory::getNewDepartment,
                EmployeePositionHistory::getOldDepartment
            );
            if (department == null) {
                department = employee.getDepartment();
            }
            return new Role(position != null ? position.getName() : null, department != null ? department.getName() : null);
        }

        /** The position on the salary record in effect on {@code date} (latest-starting wins, as in payroll). */
        private Position positionOnSalary(UUID employeeId, LocalDate date) {
            return salaries
                .getOrDefault(employeeId, List.of())
                .stream()
                .filter(c -> c.getEffectiveFrom() != null && !c.getEffectiveFrom().isAfter(date))
                .filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(date))
                .max(Comparator.comparing(Compensation::getEffectiveFrom).thenComparing(Compensation::getId, Comparator.reverseOrder()))
                .map(Compensation::getPosition)
                .orElse(null);
        }

        /**
         * From the changes of one field (those {@code changed} it, oldest first): the value the last
         * change on or before {@code date} moved to; else, the value the first later change moved
         * from; else null (no change of that field recorded).
         */
        private static <T> T asOf(
            List<EmployeePositionHistory> history,
            LocalDate date,
            Predicate<EmployeePositionHistory> changed,
            Function<EmployeePositionHistory, T> to,
            Function<EmployeePositionHistory, T> from
        ) {
            T value = null;
            for (EmployeePositionHistory h : history) {
                if (!changed.test(h) || h.getEffectiveDate() == null) {
                    continue;
                }
                if (!h.getEffectiveDate().isAfter(date)) {
                    value = to.apply(h);
                } else {
                    return value != null ? value : from.apply(h);
                }
            }
            return value;
        }
    }
}
