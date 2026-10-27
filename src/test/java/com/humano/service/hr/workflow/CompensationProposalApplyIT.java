package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.hr.PositionChangeType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.BonusType;
import com.humano.domain.hr.CompensationProposal;
import com.humano.domain.hr.EmployeePositionHistory;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.Position;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.payroll.Bonus;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.CompensationProposalRequest;
import com.humano.dto.hr.workflow.requests.GenerateCompensationProposalsRequest;
import com.humano.repository.hr.CompensationProposalRepository;
import com.humano.repository.hr.EmployeePositionHistoryRepository;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.payroll.BonusRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.hr.workflow.CompensationProposalService.Actor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applying an approved compensation proposal through the real position-change and bonus services:
 * the salary record is split on the date, the history records the promotion, payroll gets an unpaid
 * performance bonus, and the proposal is applied once. A refusal on the way leaves nothing written.
 * <p>
 * Each step commits in its own transaction, as through the API, so the fixtures are committed and
 * removed again after each test.
 */
@IntegrationTest
class CompensationProposalApplyIT {

    private static final LocalDate HIRED = LocalDate.of(2025, 1, 1);

    @Autowired
    private CompensationProposalService service;

    @Autowired
    @Qualifier("tenantTransactionManager")
    private PlatformTransactionManager txManager;

    @Autowired
    private CompensationProposalRepository proposals;

    @Autowired
    private ReviewCycleRepository cycles;

    @Autowired
    private PerformanceReviewRepository reviews;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private OrganizationalUnitRepository units;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private CurrencyRepository currencies;

    @Autowired
    private CompensationRepository compensations;

    @Autowired
    private EmployeePositionHistoryRepository histories;

    @Autowired
    private BonusRepository bonuses;

    private TransactionTemplate tx;
    private UUID employeeId;
    private UUID cycleId;
    private UUID seniorId;
    private final Actor preparer = new Actor(UUID.randomUUID(), true);
    private final Actor approver = new Actor(UUID.randomUUID(), true);

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            OrganizationalUnit unit = new OrganizationalUnit();
            unit.setName("Pay review team " + UUID.randomUUID());
            unit.setType(OrganizationalUnitType.TEAM);
            Position analyst = new Position();
            analyst.setName("Analyst " + UUID.randomUUID());
            analyst.setLevel("L2");
            analyst = positions.saveAndFlush(analyst);
            Position senior = new Position();
            senior.setName("Senior Analyst " + UUID.randomUUID());
            senior.setLevel("L3");
            seniorId = positions.saveAndFlush(senior).getId();

            String login = "pay-" + UUID.randomUUID().toString().substring(0, 8);
            Employee e = new Employee();
            e.setLogin(login);
            e.setEmail(login + "@example.com");
            e.setPassword("x".repeat(60));
            e.setActivated(true);
            e.setFirstName("Camille");
            e.setLastName("Martin");
            e.setStatus(EmployeeStatus.ACTIVE);
            e.setStartDate(HIRED);
            e.setPosition(analyst);
            e.setUnit(units.saveAndFlush(unit));
            Employee employee = employees.saveAndFlush(e);
            employeeId = employee.getId();

            Compensation salary = new Compensation();
            salary.setEmployee(employee);
            salary.setPosition(analyst);
            salary.setCurrency(currencies.findByCode(CurrencyCode.EUR).orElseThrow());
            salary.setBaseAmount(new BigDecimal("3000.00"));
            salary.setBasis(Basis.MONTHLY);
            salary.setEffectiveFrom(HIRED);
            compensations.saveAndFlush(salary);

            ReviewCycle cycle = new ReviewCycle();
            cycle.setName("Pay review cycle " + UUID.randomUUID());
            cycle.setReviewPeriodStart(LocalDate.of(2025, 1, 1));
            cycle.setReviewPeriodEnd(LocalDate.of(2025, 12, 31));
            cycle.setStartDate(LocalDate.of(2026, 1, 5));
            cycle.setEndDate(LocalDate.of(2026, 2, 27));
            cycle.setActive(false);
            cycle = cycles.saveAndFlush(cycle);
            cycleId = cycle.getId();

            PerformanceReview review = new PerformanceReview();
            review.setEmployee(employee);
            review.setCycle(cycle);
            review.setReviewDate(LocalDate.of(2026, 2, 1));
            review.setRating(5);
            review.setRecommendForPromotion(true);
            reviews.saveAndFlush(review);
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> {
            proposals.deleteAll(proposals.findByCycleId(cycleId));
            bonuses.deleteAll(
                bonuses.findAll((Specification<Bonus>) (root, q, cb) -> cb.equal(root.get("employee").get("id"), employeeId))
            );
            histories.deleteAll(histories.findByEmployeeIdOrderByEffectiveDateDescCreatedDateDesc(employeeId));
            compensations.deleteAll(compensations.findAllByEmployeeIds(List.of(employeeId)));
            reviews.deleteAll(reviews.findByCycleId(cycleId));
            cycles.deleteById(cycleId);
            Employee e = employees.findById(employeeId).orElseThrow();
            Position held = e.getPosition();
            employees.delete(e);
            units.delete(e.getUnit());
            positions.findAllById(List.of(held.getId(), seniorId)).forEach(positions::delete);
            positions.flush();
        });
    }

    @Test
    void anApprovedPromotionWithARaiseAndABonusIsAppliedOnce() {
        LocalDate date = LocalDate.now().withDayOfMonth(1);
        UUID proposalId = approvedProposal(date, new BigDecimal("3300.00"), new BigDecimal("1500.00"));

        service.apply(proposalId, approver);

        tx.executeWithoutResult(s -> {
            List<Compensation> salaries = compensations
                .findAllByEmployeeIds(List.of(employeeId))
                .stream()
                .sorted(Comparator.comparing(Compensation::getEffectiveFrom))
                .toList();
            assertThat(salaries).hasSize(2);
            assertThat(salaries.get(0).getBaseAmount()).isEqualByComparingTo("3000.00");
            assertThat(salaries.get(0).getEffectiveTo()).isEqualTo(date.minusDays(1));
            assertThat(salaries.get(1).getBaseAmount()).isEqualByComparingTo("3300.00");
            assertThat(salaries.get(1).getEffectiveFrom()).isEqualTo(date);
            assertThat(salaries.get(1).getPosition().getId()).isEqualTo(seniorId);

            EmployeePositionHistory history = histories.findByEmployeeIdOrderByEffectiveDateDescCreatedDateDesc(employeeId).get(0);
            assertThat(history.getChangeType()).isEqualTo(PositionChangeType.PROMOTION);
            assertThat(history.getNewPosition().getId()).isEqualTo(seniorId);
            assertThat(history.getNewSalary()).isEqualByComparingTo("3300.00");
            assertThat(employees.findById(employeeId).orElseThrow().getPosition().getId()).isEqualTo(seniorId);

            Bonus bonus = bonuses
                .findAll((Specification<Bonus>) (root, q, cb) -> cb.equal(root.get("employee").get("id"), employeeId))
                .get(0);
            assertThat(bonus.getType()).isEqualTo(BonusType.PERFORMANCE);
            assertThat(bonus.getAmount()).isEqualByComparingTo("1500.00");
            assertThat(bonus.getAwardDate()).isEqualTo(date);
            assertThat(bonus.getIsPaid()).isFalse();

            CompensationProposal applied = proposals.findById(proposalId).orElseThrow();
            assertThat(applied.getStatus()).isEqualTo(CompensationProposalStatus.APPLIED);
            assertThat(applied.getPositionChangeId()).isEqualTo(history.getId());
            assertThat(applied.getBonusId()).isEqualTo(bonus.getId());
        });

        assertThatThrownBy(() -> service.apply(proposalId, approver)).isInstanceOf(BadRequestAlertException.class);
        tx.executeWithoutResult(s -> assertThat(compensations.findAllByEmployeeIds(List.of(employeeId))).hasSize(2));
    }

    @Test
    void aRefusalFromThePositionChangeKeepsItApprovedWithNothingWritten() {
        // The salary record starts on the hire date, so it can't be split there.
        UUID proposalId = approvedProposal(HIRED, new BigDecimal("3300.00"), new BigDecimal("1500.00"));

        assertThatThrownBy(() -> service.apply(proposalId, approver)).isInstanceOf(BadRequestAlertException.class);

        tx.executeWithoutResult(s -> {
            assertThat(proposals.findById(proposalId).orElseThrow().getStatus()).isEqualTo(CompensationProposalStatus.APPROVED);
            assertThat(compensations.findAllByEmployeeIds(List.of(employeeId))).hasSize(1);
            assertThat(histories.findByEmployeeIdOrderByEffectiveDateDescCreatedDateDesc(employeeId)).isEmpty();
            assertThat(
                bonuses.findAll((Specification<Bonus>) (root, q, cb) -> cb.equal(root.get("employee").get("id"), employeeId))
            ).isEmpty();
        });
    }

    private UUID approvedProposal(LocalDate date, BigDecimal salary, BigDecimal bonus) {
        assertThat(service.generate(cycleId, new GenerateCompensationProposalsRequest(date), preparer)).isEqualTo(1);
        UUID id = tx.execute(s -> proposals.findByCycleId(cycleId).get(0).getId());
        service.update(id, new CompensationProposalRequest(salary, seniorId, bonus, date, "Promoted after an outstanding year"), preparer);
        service.submit(id, preparer);
        service.approve(id, approver);
        return id;
    }
}
