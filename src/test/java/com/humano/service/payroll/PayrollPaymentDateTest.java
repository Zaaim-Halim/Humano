package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollRun;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link PayrollCalendarService#updatePaymentDate}: pay may fall before the period
 * ends, never once a run for it is approved, and a calculated run goes back to draft.
 */
@ExtendWith(MockitoExtension.class)
class PayrollPaymentDateTest {

    private static final UUID PERIOD_ID = UUID.randomUUID();

    @Mock
    private PayrollCalendarRepository calendarRepository;

    @Mock
    private PayrollPeriodRepository periodRepository;

    @Mock
    private PayrollRunRepository runRepository;

    @InjectMocks
    private PayrollCalendarService service;

    private PayrollPeriod december;

    @BeforeEach
    void december() {
        PayrollCalendar calendar = new PayrollCalendar();
        calendar.setName("Default Monthly");
        december = new PayrollPeriod();
        december.setCalendar(calendar);
        december.setStartDate(LocalDate.of(2026, 12, 1));
        december.setEndDate(LocalDate.of(2026, 12, 31));
        december.setPaymentDate(LocalDate.of(2027, 1, 7));
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(december));
    }

    private PayrollRun run(RunStatus status) {
        PayrollRun run = new PayrollRun();
        run.setStatus(status);
        return run;
    }

    @Test
    void decemberPayCanMoveBeforeTheYearEnds() {
        when(runRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(periodRepository.save(december)).thenReturn(december);

        assertThat(service.updatePaymentDate(PERIOD_ID, LocalDate.of(2026, 12, 23)).paymentDate()).isEqualTo(LocalDate.of(2026, 12, 23));
    }

    @Test
    void payBeforeThePeriodStartsIsRefused() {
        assertThatThrownBy(() -> service.updatePaymentDate(PERIOD_ID, LocalDate.of(2026, 11, 30))).isInstanceOf(
            BusinessRuleViolationException.class
        );
    }

    @Test
    void anApprovedRunKeepsItsPaymentDate() {
        when(runRepository.findAll(any(Specification.class))).thenReturn(List.of(run(RunStatus.APPROVED)));

        assertThatThrownBy(() -> service.updatePaymentDate(PERIOD_ID, LocalDate.of(2026, 12, 23))).isInstanceOf(
            BusinessRuleViolationException.class
        );
        verify(periodRepository, never()).save(any());
    }

    @Test
    void aCalculatedRunGoesBackToDraftToBeRecalculated() {
        PayrollRun calculated = run(RunStatus.CALCULATED);
        when(runRepository.findAll(any(Specification.class))).thenReturn(List.of(calculated));
        when(periodRepository.save(december)).thenReturn(december);

        service.updatePaymentDate(PERIOD_ID, LocalDate.of(2026, 12, 23));

        assertThat(calculated.getStatus()).isEqualTo(RunStatus.DRAFT);
        verify(runRepository).save(calculated);
    }
}
