package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.BonusType;
import com.humano.domain.hr.CompensationProposal;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.Position;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.PositionChangeRequest;
import com.humano.dto.hr.responses.PositionChangeResponse;
import com.humano.dto.hr.workflow.requests.CompensationProposalRequest;
import com.humano.dto.hr.workflow.requests.GenerateCompensationProposalsRequest;
import com.humano.dto.hr.workflow.requests.ReturnCompensationProposalRequest;
import com.humano.dto.hr.workflow.responses.CompensationProposalResponse;
import com.humano.dto.hr.workflow.responses.CompensationReviewResponse;
import com.humano.dto.payroll.request.AwardBonusRequest;
import com.humano.dto.payroll.response.BonusResponse;
import com.humano.repository.hr.CompensationProposalRepository;
import com.humano.repository.hr.MeritGuidelinesRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.hr.PositionChangeService;
import com.humano.service.hr.workflow.CompensationProposalService.Actor;
import com.humano.service.payroll.BonusService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/** Compensation proposals: drafted from shared reviews, guided by the merit matrix, approved by four eyes, applied once. */
class CompensationProposalServiceTest {

    private final CompensationProposalRepository proposals = mock(CompensationProposalRepository.class);
    private final MeritGuidelinesRepository guidelines = mock(MeritGuidelinesRepository.class);
    private final ReviewCycleRepository cycles = mock(ReviewCycleRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PositionRepository positions = mock(PositionRepository.class);
    private final CompensationRepository compensations = mock(CompensationRepository.class);
    private final PayrollRunRepository payrollRuns = mock(PayrollRunRepository.class);
    private final PositionChangeService positionChanges = mock(PositionChangeService.class);
    private final BonusService bonuses = mock(BonusService.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final CompensationProposalService service = new CompensationProposalService(
        proposals,
        guidelines,
        cycles,
        reviews,
        employees,
        positions,
        compensations,
        payrollRuns,
        positionChanges,
        bonuses,
        audit
    );

    private final LocalDate effective = LocalDate.now().minusDays(1);
    private final Currency eur = currency();
    private final Position analyst = position("Analyst");
    private final Position senior = position("Senior Analyst");
    private final Employee hr = employee("Hana", "Hr");
    private final Employee payroll = employee("Paul", "Payroll");
    private final Employee alice = employee("Alice", "Analyst");
    private final Actor asHr = new Actor(hr.getId(), true);
    private final Actor asPayroll = new Actor(payroll.getId(), true);
    private final Actor asAlice = new Actor(alice.getId(), true);

    private ReviewCycle cycle;
    private final List<CompensationProposal> saved = new ArrayList<>();
    private final List<Compensation> salaries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setName("2026 Annual Review");
        cycle.setType(ReviewCycleType.REGULAR);
        when(cycles.findById(cycle.getId())).thenReturn(Optional.of(cycle));
        when(guidelines.findAll()).thenReturn(List.of());
        when(proposals.save(any())).thenAnswer(inv -> {
            CompensationProposal p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                saved.add(p);
            }
            when(proposals.findWithDetailsById(p.getId())).thenReturn(Optional.of(p));
            when(proposals.findForUpdate(p.getId())).thenReturn(Optional.of(p));
            return p;
        });
        when(proposals.findByCycleId(cycle.getId())).thenAnswer(inv -> List.copyOf(saved));
        when(compensations.findAllByEmployeeIds(any())).thenAnswer(inv -> List.copyOf(salaries));
        when(employees.findById(any())).thenAnswer(inv ->
            Optional.ofNullable(
                List.of(hr, payroll, alice).stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst().orElse(null)
            )
        );
        when(positions.findById(senior.getId())).thenReturn(Optional.of(senior));
        alice.setPosition(analyst);
        salaries.add(salary(alice, "4000.00", effective.minusYears(1)));
    }

    @Test
    void draftsAreMadeForSharedRatedReviewsOnlyWithTheCurrentPayAndNoIncrease() {
        Employee bob = employee("Bob", "Builder");
        Employee leaver = employee("Lea", "Leaver");
        leaver.setStatus(EmployeeStatus.OFFBOARDING);
        PerformanceReview disputed = review(bob, 3);
        disputed.setDisputed(true);
        PerformanceReview unrated = review(bob, 3);
        unrated.setRating(null);
        PerformanceReview aliceReview = review(alice, 4);
        aliceReview.setRecommendForPromotion(true);
        when(reviews.findByCycleIdAndStatusIn(eq(cycle.getId()), any())).thenReturn(
            List.of(aliceReview, disputed, unrated, review(leaver, 3))
        );

        assertThat(service.generate(cycle.getId(), new GenerateCompensationProposalsRequest(effective), asHr)).isEqualTo(1);

        CompensationProposal draft = saved.get(0);
        assertThat(draft.getEmployee()).isEqualTo(alice);
        assertThat(draft.getStatus()).isEqualTo(CompensationProposalStatus.DRAFT);
        assertThat(draft.getRating()).isEqualTo(4);
        assertThat(draft.isPromotionRecommended()).isTrue();
        assertThat(draft.getCurrentSalary()).isEqualByComparingTo("4000");
        assertThat(draft.getCurrency()).isEqualTo(eur);
        assertThat(draft.getBasis()).isEqualTo(Basis.MONTHLY);
        assertThat(draft.getCurrentPosition()).isEqualTo(analyst);
        assertThat(draft.getNewSalary()).isNull();

        // Drafting again skips reviews that already have one.
        when(proposals.findReviewIdsByCycleId(cycle.getId())).thenReturn(List.of(aliceReview.getId()));
        assertThat(service.generate(cycle.getId(), new GenerateCompensationProposalsRequest(effective), asHr)).isZero();

        ReviewCycle probation = new ReviewCycle();
        probation.setId(UUID.randomUUID());
        probation.setType(ReviewCycleType.PROBATION);
        when(cycles.findById(probation.getId())).thenReturn(Optional.of(probation));
        assertThatThrownBy(() -> service.generate(probation.getId(), new GenerateCompensationProposalsRequest(effective), asHr)
        ).hasMessageContaining("regular review cycles");
    }

    @Test
    void theMeritMatrixSuggestsTheMidpointAndFlagsIncreasesOutsideItsRange() {
        CompensationProposal p = draft(4);
        assertThat(service.useGuidance(cycle.getId(), asHr)).isEqualTo(1);
        // Rating 4: 3–5 %, midpoint 4 %.
        assertThat(p.getNewSalary()).isEqualByComparingTo("4160.00");

        CompensationProposalResponse inside = service.update(p.getId(), change("4160.00", null, null), asHr);
        assertThat(inside.increasePercent()).isEqualByComparingTo("4.00");
        assertThat(inside.outsideGuidance()).isFalse();

        CompensationProposalResponse outside = service.update(p.getId(), change("4600.00", null, null), asHr);
        assertThat(outside.increasePercent()).isEqualByComparingTo("15.00");
        assertThat(outside.outsideGuidance()).isTrue();

        // A salary set by hand is kept.
        assertThat(service.useGuidance(cycle.getId(), asHr)).isZero();
        assertThat(p.getNewSalary()).isEqualByComparingTo("4600.00");
    }

    @Test
    void someoneElseApprovesAndNobodyActsOnTheirOwn() {
        CompensationProposal p = draft(3);
        CompensationProposal own = draft(3);
        own.setEmployee(hr);

        assertThatThrownBy(() -> service.submit(p.getId(), asAlice)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.submitAll(cycle.getId(), asHr)).isEqualTo(1);
        assertThat(own.getStatus()).isEqualTo(CompensationProposalStatus.DRAFT);
        assertThat(p.getSubmittedByName()).isEqualTo("Hana Hr");

        assertThat(service.review(cycle.getId(), asHr).proposals())
            .filteredOn(r -> r.id().equals(p.getId()))
            .first()
            .satisfies(r -> assertThat(r.actions().approve()).isFalse());
        assertThatThrownBy(() -> service.approve(p.getId(), asHr)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.approveAll(cycle.getId(), asHr)).isZero();

        assertThat(service.approveAll(cycle.getId(), asPayroll)).isEqualTo(1);
        assertThat(p.getStatus()).isEqualTo(CompensationProposalStatus.APPROVED);
        assertThat(p.getApprovedByName()).isEqualTo("Paul Payroll");
        verify(audit).record(eq("COMPENSATION_PROPOSAL_APPROVED"), anyString(), eq(p.getId().toString()), any());
    }

    @Test
    void whoeverSetTheNumbersDoesNotApproveThemEvenWhenSomeoneElseSubmitted() {
        CompensationProposal p = draft(4);
        Actor third = new Actor(UUID.randomUUID(), true);
        service.update(p.getId(), change("4200.00", null, null), asPayroll);
        assertThat(p.getPreparedById()).isEqualTo(payroll.getId());
        service.submit(p.getId(), asHr);

        assertThatThrownBy(() -> service.approve(p.getId(), asPayroll)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.approveAll(cycle.getId(), asPayroll)).isZero();
        assertThatThrownBy(() -> service.approve(p.getId(), asHr)).isInstanceOf(AccessDeniedException.class);

        assertThat(service.approve(p.getId(), third).status()).isEqualTo(CompensationProposalStatus.APPROVED);

        // Filling a draft from the merit matrix prepares it too.
        CompensationProposal other = draft(3);
        service.useGuidance(cycle.getId(), asHr);
        assertThat(other.getPreparedById()).isEqualTo(hr.getId());
    }

    @Test
    void onesOwnProposalStaysOutOfOnesViewAndTotals() {
        draft(3);
        CompensationProposal own = draft(3);
        own.setEmployee(hr);

        CompensationReviewResponse review = service.review(cycle.getId(), asHr);

        assertThat(review.proposals()).extracting(CompensationProposalResponse::employeeId).containsExactly(alice.getId());
        assertThat(review.totals().get(0).count()).isEqualTo(1);
        assertThat(service.review(cycle.getId(), asPayroll).proposals()).hasSize(2);
    }

    @Test
    void returningToDraftClearsTheApprovalAndKeepsWhy() {
        CompensationProposal p = approved(draft(3));

        service.returnToDraft(p.getId(), new ReturnCompensationProposalRequest(" Move the date after the June payroll "), asHr);

        assertThat(p.getStatus()).isEqualTo(CompensationProposalStatus.DRAFT);
        assertThat(p.getReturnComment()).isEqualTo("Move the date after the June payroll");
        assertThat(p.getSubmittedById()).isNull();
        assertThat(p.getApprovedById()).isNull();
        assertThatThrownBy(() -> service.returnToDraft(p.getId(), new ReturnCompensationProposalRequest("again"), asHr)
        ).hasMessageContaining("submitted or approved");
    }

    @Test
    void applyingPromotesRaisesAndAwardsAnUnpaidPerformanceBonusOnce() {
        CompensationProposal p = draft(5);
        service.update(p.getId(), change("4400.00", senior.getId(), "1000.00"), asHr);
        approved(p);
        UUID historyId = UUID.randomUUID();
        UUID bonusId = UUID.randomUUID();
        PositionChangeResponse history = mock(PositionChangeResponse.class);
        when(history.id()).thenReturn(historyId);
        BonusResponse bonus = mock(BonusResponse.class);
        when(bonus.id()).thenReturn(bonusId);
        when(positionChanges.change(eq(alice.getId()), any())).thenReturn(history);
        when(bonuses.awardBonus(any())).thenReturn(bonus);

        CompensationProposalResponse applied = service.apply(p.getId(), asPayroll);

        ArgumentCaptor<PositionChangeRequest> change = ArgumentCaptor.forClass(PositionChangeRequest.class);
        verify(positionChanges).change(eq(alice.getId()), change.capture());
        assertThat(change.getValue().changeType()).isEqualTo(PositionChangeType.PROMOTION);
        assertThat(change.getValue().newPositionId()).isEqualTo(senior.getId());
        assertThat(change.getValue().newSalary()).isEqualByComparingTo("4400.00");
        assertThat(change.getValue().effectiveDate()).isEqualTo(effective);
        ArgumentCaptor<AwardBonusRequest> award = ArgumentCaptor.forClass(AwardBonusRequest.class);
        verify(bonuses).awardBonus(award.capture());
        assertThat(award.getValue().type()).isEqualTo(BonusType.PERFORMANCE);
        assertThat(award.getValue().currencyId()).isEqualTo(eur.getId());
        assertThat(award.getValue().payImmediately()).isFalse();

        assertThat(applied.status()).isEqualTo(CompensationProposalStatus.APPLIED);
        assertThat(p.getPositionChangeId()).isEqualTo(historyId);
        assertThat(p.getBonusId()).isEqualTo(bonusId);
        assertThat(applied.actions().apply()).isFalse();
        assertThatThrownBy(() -> service.apply(p.getId(), asPayroll)).hasMessageContaining("Only an approved");
    }

    @Test
    void applyingIsRefusedBeforeAnythingChangesWhenItCannotHold() {
        CompensationProposal p = draft(3);
        service.update(p.getId(), change("4100.00", null, "500.00"), asHr);
        approved(p);

        assertThatThrownBy(() -> service.apply(p.getId(), new Actor(payroll.getId(), false))).isInstanceOf(AccessDeniedException.class);

        when(payrollRuns.findLatestPeriodEndWithStatusIn(any())).thenReturn(effective);
        assertThatThrownBy(() -> service.apply(p.getId(), asPayroll)).hasMessageContaining("already approved or posted");
        when(payrollRuns.findLatestPeriodEndWithStatusIn(any())).thenReturn(null);

        salaries.add(salary(alice, "4050.00", effective.minusDays(10)));
        assertThatThrownBy(() -> service.apply(p.getId(), asPayroll)).hasMessageContaining("changed since this was prepared");
        assertThat(service.review(cycle.getId(), asPayroll).proposals().get(0).payChanged()).isTrue();

        p.setEffectiveDate(LocalDate.now().plusDays(5));
        assertThatThrownBy(() -> service.apply(p.getId(), asPayroll)).hasMessageContaining("apply it on or after");

        verify(positionChanges, never()).change(any(), any());
        verify(bonuses, never()).awardBonus(any());
        assertThat(p.getStatus()).isEqualTo(CompensationProposalStatus.APPROVED);
    }

    @Test
    void totalsAddUpPerCurrencyAndBasis() {
        CompensationProposal p = draft(4);
        service.update(p.getId(), change("4200.00", null, "300.00"), asHr);
        CompensationProposal unchanged = draft(3);

        CompensationReviewResponse review = service.review(cycle.getId(), asHr);

        assertThat(review.totals()).hasSize(1);
        CompensationReviewResponse.Total total = review.totals().get(0);
        assertThat(total.currencyCode()).isEqualTo("EUR");
        assertThat(total.basis()).isEqualTo(Basis.MONTHLY);
        assertThat(total.count()).isEqualTo(2);
        assertThat(total.current()).isEqualByComparingTo("8000.00");
        assertThat(total.proposed()).isEqualByComparingTo("8200.00");
        assertThat(total.increasePercent()).isEqualByComparingTo("2.50");
        assertThat(total.bonuses()).isEqualByComparingTo("300.00");
        assertThat(unchanged.getNewSalary()).isNull();
    }

    // ---------------------------------------------------------------- fixtures

    private CompensationProposal draft(int rating) {
        CompensationProposal p = new CompensationProposal();
        p.setCycle(cycle);
        p.setReview(review(alice, rating));
        p.setEmployee(alice);
        p.setRating(rating);
        p.setEffectiveDate(effective);
        p.setCurrentSalary(new BigDecimal("4000.00"));
        p.setCurrency(eur);
        p.setBasis(Basis.MONTHLY);
        p.setCurrentPosition(analyst);
        return proposals.save(p);
    }

    private CompensationProposal approved(CompensationProposal p) {
        p.setStatus(CompensationProposalStatus.APPROVED);
        p.setSubmittedById(hr.getId());
        p.setApprovedById(payroll.getId());
        p.setApprovedAt(Instant.now());
        return p;
    }

    private CompensationProposalRequest change(String salary, UUID positionId, String bonus) {
        return new CompensationProposalRequest(
            salary != null ? new BigDecimal(salary) : null,
            positionId,
            bonus != null ? new BigDecimal(bonus) : null,
            effective,
            "Strong year"
        );
    }

    private PerformanceReview review(Employee employee, int rating) {
        PerformanceReview r = new PerformanceReview();
        r.setId(UUID.randomUUID());
        r.setEmployee(employee);
        r.setStatus(PerformanceReviewStatus.SHARED);
        r.setRating(rating);
        r.setCycle(cycle);
        return r;
    }

    private Compensation salary(Employee employee, String amount, LocalDate from) {
        Compensation c = new Compensation();
        c.setId(UUID.randomUUID());
        c.setEmployee(employee);
        c.setBaseAmount(new BigDecimal(amount));
        c.setBasis(Basis.MONTHLY);
        c.setCurrency(eur);
        c.setEffectiveFrom(from);
        c.setPosition(employee.getPosition());
        return c;
    }

    private static Currency currency() {
        Currency c = new Currency();
        c.setId(UUID.randomUUID());
        c.setCode(CurrencyCode.EUR);
        return c;
    }

    private static Position position(String name) {
        Position p = new Position();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private static Employee employee(String firstName, String lastName) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }
}
