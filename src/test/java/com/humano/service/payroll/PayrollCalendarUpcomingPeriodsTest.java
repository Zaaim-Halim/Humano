package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.dto.payroll.response.PayrollPeriodResponse;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link PayrollCalendarService#createUpcomingPeriods}: the periods a new run can pick
 * from, from the current one to three months ahead, whole periods only.
 */
@ExtendWith(MockitoExtension.class)
class PayrollCalendarUpcomingPeriodsTest {

    private static final UUID CALENDAR_ID = UUID.randomUUID();

    @Mock
    private PayrollCalendarRepository calendarRepository;

    @Mock
    private PayrollPeriodRepository periodRepository;

    @InjectMocks
    private PayrollCalendarService service;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void saveAllReturnsWhatItSaves() {
        when(periodRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void calendar(Frequency frequency, List<PayrollPeriod> existing) {
        PayrollCalendar calendar = new PayrollCalendar();
        calendar.setName("Default Monthly");
        calendar.setFrequency(frequency);
        calendar.setTimezone(TimeZone.getTimeZone("UTC"));
        calendar.setActive(true);
        when(calendarRepository.findById(CALENDAR_ID)).thenReturn(Optional.of(calendar));
        when(periodRepository.findAll(any(Specification.class))).thenReturn(existing);
    }

    private static PayrollPeriod period(LocalDate start, LocalDate end) {
        PayrollPeriod p = new PayrollPeriod();
        p.setStartDate(start);
        p.setEndDate(end);
        return p;
    }

    @Test
    void monthlyCalendarWithoutPeriodsGetsWholeMonthsFromThisOneToThreeMonthsAhead() {
        calendar(Frequency.MONTHLY, List.of());

        List<PayrollPeriodResponse> created = service.createUpcomingPeriods(CALENDAR_ID);

        YearMonth month = YearMonth.from(today);
        assertThat(created).hasSize(4);
        for (int i = 0; i < created.size(); i++) {
            assertThat(created.get(i).startDate()).isEqualTo(month.plusMonths(i).atDay(1));
            assertThat(created.get(i).endDate()).isEqualTo(month.plusMonths(i).atEndOfMonth());
            assertThat(created.get(i).paymentDate()).isAfter(created.get(i).endDate());
        }
    }

    @Test
    void continuesAfterTheLatestRecentPeriod() {
        YearMonth month = YearMonth.from(today);
        calendar(Frequency.MONTHLY, List.of(period(month.atDay(1), month.atEndOfMonth())));

        List<PayrollPeriodResponse> created = service.createUpcomingPeriods(CALENDAR_ID);

        assertThat(created.get(0).startDate()).isEqualTo(month.plusMonths(1).atDay(1));
        assertThat(created.get(created.size() - 1).startDate()).isEqualTo(month.plusMonths(3).atDay(1));
    }

    @Test
    void startsAtTheCurrentPeriodWhenTheLatestEndedLongAgo() {
        YearMonth lastYear = YearMonth.from(today).minusYears(1);
        calendar(Frequency.MONTHLY, List.of(period(lastYear.atDay(1), lastYear.atEndOfMonth())));

        List<PayrollPeriodResponse> created = service.createUpcomingPeriods(CALENDAR_ID);

        assertThat(created.get(0).startDate()).isEqualTo(YearMonth.from(today).atDay(1));
    }

    @Test
    void weeklyCalendarStartsOnThisWeeksMondayInWholeWeeks() {
        calendar(Frequency.WEEKLY, List.of());

        List<PayrollPeriodResponse> created = service.createUpcomingPeriods(CALENDAR_ID);

        assertThat(created.get(0).startDate()).isEqualTo(today.with(DayOfWeek.MONDAY));
        assertThat(created).allSatisfy(p -> assertThat(p.endDate()).isEqualTo(p.startDate().plusDays(6)));
        assertThat(created.get(created.size() - 1).startDate()).isBeforeOrEqualTo(today.plusMonths(3));
        assertThat(created.get(created.size() - 1).endDate()).isAfter(today.plusMonths(3));
    }
}
