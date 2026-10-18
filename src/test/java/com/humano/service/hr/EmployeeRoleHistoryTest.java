package com.humano.service.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.EmployeePositionHistory;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.shared.Employee;
import com.humano.repository.hr.EmployeePositionHistoryRepository;
import com.humano.repository.payroll.CompensationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A payslip shows the position and department of its period, not today's. */
class EmployeeRoleHistoryTest {

    private final CompensationRepository salaries = mock(CompensationRepository.class);
    private final EmployeePositionHistoryRepository changes = mock(EmployeePositionHistoryRepository.class);
    private final EmployeeRoleHistory roles = new EmployeeRoleHistory(salaries, changes);

    private final Employee lucas = new Employee();
    private final Position engineer = position("Software Engineer");
    private final Position senior = position("Senior Software Engineer");
    private final Department engineering = department("Engineering");
    private final Department platform = department("Platform");

    @BeforeEach
    void setUp() {
        lucas.setId(UUID.randomUUID());
        // Today: promoted (Oct 15) and moved to Platform (Nov 1).
        lucas.setPosition(senior);
        lucas.setDepartment(platform);
        when(salaries.findAllByEmployeeIds(anyCollection())).thenReturn(
            List.of(
                salary(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 10, 14), engineer),
                salary(LocalDate.of(2026, 10, 15), null, senior)
            )
        );
        EmployeePositionHistory move = new EmployeePositionHistory();
        move.setEmployee(lucas);
        move.setStatus(PositionChangeStatus.APPLIED);
        move.setEffectiveDate(LocalDate.of(2026, 11, 1));
        move.setOldDepartment(engineering);
        move.setNewDepartment(platform);
        when(changes.findByEmployeeIdInAndStatus(anyCollection(), any())).thenReturn(List.of(move));
    }

    @Test
    void anEarlierPeriodKeepsItsPositionAndDepartment() {
        EmployeeRoleHistory.Role august = roles.at(lucas, LocalDate.of(2026, 8, 31));

        assertThat(august.position()).isEqualTo("Software Engineer");
        assertThat(august.department()).isEqualTo("Engineering");
    }

    @Test
    void thePeriodOfTheChangeShowsTheNewPosition() {
        EmployeeRoleHistory.Role october = roles.at(lucas, LocalDate.of(2026, 10, 31));

        assertThat(october.position()).isEqualTo("Senior Software Engineer");
        assertThat(october.department()).isEqualTo("Engineering");
    }

    @Test
    void afterTheMoveTheNewDepartmentShows() {
        assertThat(roles.at(lucas, LocalDate.of(2026, 11, 30)).department()).isEqualTo("Platform");
    }

    @Test
    void withNothingRecordedTheCurrentValuesShow() {
        when(salaries.findAllByEmployeeIds(anyCollection())).thenReturn(List.of());
        when(changes.findByEmployeeIdInAndStatus(anyCollection(), any())).thenReturn(List.of());

        EmployeeRoleHistory.Role role = roles.at(lucas, LocalDate.of(2026, 8, 31));

        assertThat(role.position()).isEqualTo("Senior Software Engineer");
        assertThat(role.department()).isEqualTo("Platform");
    }

    private Compensation salary(LocalDate from, LocalDate to, Position position) {
        Compensation c = new Compensation();
        c.setId(UUID.randomUUID());
        c.setEmployee(lucas);
        c.setBaseAmount(BigDecimal.TEN);
        c.setEffectiveFrom(from);
        c.setEffectiveTo(to);
        c.setPosition(position);
        return c;
    }

    private static Position position(String name) {
        Position p = new Position();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private static Department department(String name) {
        Department d = new Department();
        d.setId(UUID.randomUUID());
        d.setName(name);
        return d;
    }
}
