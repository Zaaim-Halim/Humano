package com.humano.service.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.EmployeePositionHistory;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.PositionChangeRequest;
import com.humano.dto.hr.responses.PositionChangeResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.EmployeePositionHistoryRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** A promotion, transfer or raise is one dated, recorded step that keeps HR and payroll in line. */
class PositionChangeServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PositionRepository positions = mock(PositionRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final EmployeePositionHistoryRepository history = mock(EmployeePositionHistoryRepository.class);
    private final CompensationRepository salaries = mock(CompensationRepository.class);
    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final PositionChangeService service = new PositionChangeService(
        employees,
        positions,
        departments,
        history,
        salaries,
        runs,
        audit
    );

    private final Employee lucas = new Employee();
    private final Position engineer = position("Software Engineer");
    private final Position senior = position("Senior Software Engineer");
    private final Department engineering = department("Engineering");
    private final Department platform = department("Platform");
    private final List<Compensation> records = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lucas.setId(UUID.randomUUID());
        lucas.setFirstName("Lucas");
        lucas.setLastName("Moreau");
        lucas.setStartDate(TODAY.minusYears(1));
        lucas.setPosition(engineer);
        lucas.setJobTitle("Software Engineer");
        lucas.setDepartment(engineering);
        records.add(salary(new BigDecimal("9200"), TODAY.minusYears(1), null, engineer));

        when(employees.findById(lucas.getId())).thenReturn(Optional.of(lucas));
        when(positions.findById(senior.getId())).thenReturn(Optional.of(senior));
        when(positions.findById(engineer.getId())).thenReturn(Optional.of(engineer));
        when(departments.findById(platform.getId())).thenReturn(Optional.of(platform));
        when(salaries.findAllByEmployeeIds(anyCollection())).thenAnswer(i -> List.copyOf(records));
        when(salaries.save(any())).thenAnswer(i -> {
            Compensation c = i.getArgument(0);
            if (!records.contains(c)) records.add(c);
            return c;
        });
        when(history.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void promotionWithARaiseSplitsTheSalaryRecordOnTheDate() {
        LocalDate date = TODAY.minusDays(3);

        PositionChangeResponse response = service.change(lucas.getId(), request(date, senior.getId(), null, "11000"));

        assertThat(records).hasSize(2);
        Compensation before = records.get(0);
        Compensation after = records.get(1);
        assertThat(before.getEffectiveTo()).isEqualTo(date.minusDays(1));
        assertThat(after.getEffectiveFrom()).isEqualTo(date);
        assertThat(after.getBaseAmount()).isEqualByComparingTo("11000");
        assertThat(after.getPosition()).isSameAs(senior);
        assertThat(after.getBasis()).isEqualTo(Basis.MONTHLY);

        assertThat(lucas.getPosition()).isSameAs(senior);
        assertThat(lucas.getJobTitle()).isEqualTo("Senior Software Engineer");

        assertThat(response.status()).isEqualTo(PositionChangeStatus.APPLIED);
        assertThat(response.changeType()).isEqualTo(PositionChangeType.PROMOTION);
        assertThat(response.oldPosition().name()).isEqualTo("Software Engineer");
        assertThat(response.newPosition().name()).isEqualTo("Senior Software Engineer");
        assertThat(response.oldSalary()).isEqualByComparingTo("9200");
        assertThat(response.newSalary()).isEqualByComparingTo("11000");
        assertThat(response.reason()).isEqualTo("Strong year");
        verify(audit).record(any(), any(), any(), any());
    }

    @Test
    void aPositionChangeAloneMovesTheSalaryRecordToTheNewPosition() {
        service.change(lucas.getId(), request(TODAY, senior.getId(), null, null));

        Compensation after = records.get(1);
        assertThat(after.getPosition()).isSameAs(senior);
        assertThat(after.getBaseAmount()).isEqualByComparingTo("9200");
    }

    @Test
    void aDepartmentMoveIsRecordedWithoutTouchingPay() {
        service.change(lucas.getId(), new PositionChangeRequest(PositionChangeType.TRANSFER, TODAY, null, platform.getId(), null, "Reorg"));

        ArgumentCaptor<EmployeePositionHistory> saved = ArgumentCaptor.forClass(EmployeePositionHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getOldDepartment()).isSameAs(engineering);
        assertThat(saved.getValue().getNewDepartment()).isSameAs(platform);
        assertThat(saved.getValue().getNewPosition()).isNull();
        assertThat(lucas.getDepartment()).isSameAs(platform);
        assertThat(records).hasSize(1);
        verify(salaries, never()).save(any());
    }

    @Test
    void aChangeOnTheDayTheSalaryRecordStartsIsRefused() {
        LocalDate start = TODAY.minusDays(10);
        records.set(0, salary(new BigDecimal("9200"), start, null, engineer));

        assertThatThrownBy(() -> service.change(lucas.getId(), request(start, senior.getId(), null, "11000")))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("already starts on");
        assertThat(records.get(0).getEffectiveTo()).isNull();
    }

    @Test
    void aChangeBeforeALaterSalaryChangeIsRefused() {
        records.add(salary(new BigDecimal("9500"), TODAY.minusDays(2), null, engineer));
        records.get(0).setEffectiveTo(TODAY.minusDays(3));

        assertThatThrownBy(() -> service.change(lucas.getId(), request(TODAY.minusDays(5), senior.getId(), null, "11000")))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("later salary change");
    }

    @Test
    void aChangeInsidePayrollAlreadyApprovedOrPostedIsRefused() {
        when(runs.findLatestPeriodEndWithStatusIn(any())).thenReturn(TODAY.minusDays(1));

        assertThatThrownBy(() -> service.change(lucas.getId(), request(TODAY.minusDays(1), senior.getId(), null, null)))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("already approved or posted");
        verify(history, never()).save(any());
    }

    @Test
    void aFutureDateIsRefusedForNow() {
        assertThatThrownBy(() -> service.change(lucas.getId(), request(TODAY.plusDays(1), senior.getId(), null, null)))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("future");
    }

    @Test
    void aChangeThatChangesNothingIsRefused() {
        assertThatThrownBy(() -> service.change(lucas.getId(), request(TODAY, engineer.getId(), null, "9200")))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("Nothing changes");
    }

    @Test
    void aRaiseWithoutASalaryRecordIsRefused() {
        records.clear();

        assertThatThrownBy(() -> service.change(lucas.getId(), request(TODAY, null, null, "11000")))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("no salary record");
    }

    @Test
    void theHistoryHidesPayFromThoseWhoMayNotSeeIt() {
        EmployeePositionHistory row = new EmployeePositionHistory();
        row.setEmployee(lucas);
        row.setChangeType(PositionChangeType.PROMOTION);
        row.setStatus(PositionChangeStatus.APPLIED);
        row.setEffectiveDate(TODAY);
        row.setOldSalary(new BigDecimal("9200"));
        row.setNewSalary(new BigDecimal("11000"));
        when(history.findByEmployeeIdOrderByEffectiveDateDescCreatedDateDesc(lucas.getId())).thenReturn(List.of(row));

        assertThat(service.history(lucas.getId(), false).get(0).newSalary()).isNull();
        assertThat(service.history(lucas.getId(), true).get(0).newSalary()).isEqualByComparingTo("11000");
    }

    private PositionChangeRequest request(LocalDate date, UUID positionId, UUID departmentId, String salary) {
        return new PositionChangeRequest(
            PositionChangeType.PROMOTION,
            date,
            positionId,
            departmentId,
            salary != null ? new BigDecimal(salary) : null,
            "Strong year"
        );
    }

    private Compensation salary(BigDecimal amount, LocalDate from, LocalDate to, Position position) {
        Compensation c = new Compensation();
        c.setId(UUID.randomUUID());
        c.setEmployee(lucas);
        c.setBaseAmount(amount);
        c.setBasis(Basis.MONTHLY);
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
