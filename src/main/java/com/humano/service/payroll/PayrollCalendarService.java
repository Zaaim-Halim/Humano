package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.dto.payroll.request.CreatePayrollCalendarRequest;
import com.humano.dto.payroll.request.GeneratePayrollPeriodsRequest;
import com.humano.dto.payroll.response.PayrollCalendarResponse;
import com.humano.dto.payroll.response.PayrollPeriodResponse;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing payroll calendars and generating payroll periods.
 * Supports multiple frequencies and automatic period generation.
 */
@Service
@Transactional
public class PayrollCalendarService {

    private static final Logger log = LoggerFactory.getLogger(PayrollCalendarService.class);

    private final PayrollCalendarRepository calendarRepository;
    private final PayrollPeriodRepository periodRepository;

    public PayrollCalendarService(PayrollCalendarRepository calendarRepository, PayrollPeriodRepository periodRepository) {
        this.calendarRepository = calendarRepository;
        this.periodRepository = periodRepository;
    }

    /**
     * Creates a new payroll calendar.
     */
    public PayrollCalendarResponse createCalendar(CreatePayrollCalendarRequest request) {
        log.debug("Creating payroll calendar: {}", request.name());

        // Validate timezone
        TimeZone timezone;
        try {
            timezone = TimeZone.getTimeZone(request.timezone());
            if (!timezone.getID().equals(request.timezone())) {
                throw new BusinessRuleViolationException("Invalid timezone: " + request.timezone());
            }
        } catch (Exception e) {
            throw new BusinessRuleViolationException("Invalid timezone: " + request.timezone());
        }

        PayrollCalendar calendar = new PayrollCalendar();
        calendar.setName(request.name());
        calendar.setFrequency(request.frequency());
        calendar.setTimezone(timezone);
        calendar.setActive(request.active());

        calendar = calendarRepository.save(calendar);
        log.info("Created payroll calendar {} with frequency {}", calendar.getId(), request.frequency());

        return toCalendarResponse(calendar);
    }

    /**
     * Generates payroll periods for a calendar within a date range.
     */
    public List<PayrollPeriodResponse> generatePeriods(GeneratePayrollPeriodsRequest request) {
        log.info("Generating payroll periods for calendar {} from {} to {}", request.calendarId(), request.startDate(), request.endDate());

        PayrollCalendar calendar = calendarRepository
            .findById(request.calendarId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollCalendar", request.calendarId()));

        if (!calendar.getActive()) {
            throw new BusinessRuleViolationException("Cannot generate periods for inactive calendar");
        }

        List<PayrollPeriod> newPeriods = new ArrayList<>();
        LocalDate currentStart = request.startDate();

        while (currentStart.isBefore(request.endDate())) {
            LocalDate periodEnd = calculatePeriodEnd(currentStart, calendar.getFrequency());

            // Ensure period doesn't exceed requested end date
            if (periodEnd.isAfter(request.endDate())) {
                periodEnd = request.endDate();
            }

            String periodCode = generatePeriodCode(calendar, currentStart);

            // Check if period already exists
            if (request.skipExistingPeriods()) {
                boolean exists = periodRepository.exists(
                    (Specification<PayrollPeriod>) (root, query, cb) ->
                        cb.and(cb.equal(root.get("calendar").get("id"), calendar.getId()), cb.equal(root.get("code"), periodCode))
                );
                if (exists) {
                    currentStart = periodEnd.plusDays(1);
                    continue;
                }
            }

            LocalDate paymentDate = calculatePaymentDate(periodEnd, request.paymentDayOffset());

            PayrollPeriod period = new PayrollPeriod();
            period.setCalendar(calendar);
            period.setCode(periodCode);
            period.setStartDate(currentStart);
            period.setEndDate(periodEnd);
            period.setPaymentDate(paymentDate);
            period.setClosed(false);

            newPeriods.add(period);
            currentStart = periodEnd.plusDays(1);
        }

        List<PayrollPeriod> savedPeriods = periodRepository.saveAll(newPeriods);
        log.info("Generated {} payroll periods for calendar {}", savedPeriods.size(), calendar.getId());

        return savedPeriods.stream().map(this::toPeriodResponse).toList();
    }

    /**
     * Gets all active payroll calendars.
     */
    @Transactional(readOnly = true)
    public List<PayrollCalendarResponse> getActiveCalendars() {
        return calendarRepository
            .findAll((Specification<PayrollCalendar>) (root, query, cb) -> cb.isTrue(root.get("active")))
            .stream()
            .map(this::toCalendarResponse)
            .toList();
    }

    /**
     * Gets periods for a calendar with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PayrollPeriodResponse> getPeriods(UUID calendarId, Pageable pageable) {
        return periodRepository
            .findAll(
                (Specification<PayrollPeriod>) (root, query, cb) -> {
                    if (query != null) {
                        query.orderBy(cb.desc(root.get("startDate")));
                    }
                    return cb.equal(root.get("calendar").get("id"), calendarId);
                },
                pageable
            )
            .map(this::toPeriodResponse);
    }

    /**
     * Gets open (not closed) periods ready for payroll processing.
     */
    @Transactional(readOnly = true)
    public List<PayrollPeriodResponse> getOpenPeriods(UUID calendarId) {
        LocalDate today = LocalDate.now();

        return periodRepository
            .findAll(
                (Specification<PayrollPeriod>) (root, query, cb) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.isFalse(root.get("closed")));
                    predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), today));
                    if (calendarId != null) {
                        predicates.add(cb.equal(root.get("calendar").get("id"), calendarId));
                    }
                    if (query != null) {
                        query.orderBy(cb.asc(root.get("startDate")));
                    }
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }
            )
            .stream()
            .map(this::toPeriodResponse)
            .toList();
    }

    /**
     * Closes a payroll period after payroll is posted.
     */
    public PayrollPeriodResponse closePeriod(UUID periodId) {
        PayrollPeriod period = periodRepository
            .findById(periodId)
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", periodId));

        if (period.isClosed()) {
            throw new BusinessRuleViolationException("Period is already closed");
        }

        period.setClosed(true);
        period = periodRepository.save(period);

        log.info("Closed payroll period {}", periodId);
        return toPeriodResponse(period);
    }

    /**
     * Reopens a closed payroll period (requires special permission).
     */
    public PayrollPeriodResponse reopenPeriod(UUID periodId, String reason) {
        PayrollPeriod period = periodRepository
            .findById(periodId)
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", periodId));

        if (!period.isClosed()) {
            throw new BusinessRuleViolationException("Period is not closed");
        }

        period.setClosed(false);
        period = periodRepository.save(period);

        log.warn("Reopened payroll period {} - Reason: {}", periodId, reason);
        return toPeriodResponse(period);
    }

    /**
     * Updates payment date for a period.
     */
    public PayrollPeriodResponse updatePaymentDate(UUID periodId, LocalDate newPaymentDate) {
        PayrollPeriod period = periodRepository
            .findById(periodId)
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", periodId));

        if (period.isClosed()) {
            throw new BusinessRuleViolationException("Cannot update payment date for closed period");
        }

        if (newPaymentDate.isBefore(period.getEndDate())) {
            throw new BusinessRuleViolationException("Payment date cannot be before period end date");
        }

        period.setPaymentDate(newPaymentDate);
        period = periodRepository.save(period);

        log.info("Updated payment date for period {} to {}", periodId, newPaymentDate);
        return toPeriodResponse(period);
    }

    /**
     * Activates or deactivates a calendar.
     */
    public PayrollCalendarResponse setCalendarActive(UUID calendarId, boolean active) {
        PayrollCalendar calendar = calendarRepository
            .findById(calendarId)
            .orElseThrow(() -> new EntityNotFoundException("PayrollCalendar", calendarId));

        calendar.setActive(active);
        calendar = calendarRepository.save(calendar);

        log.info("{} payroll calendar {}", active ? "Activated" : "Deactivated", calendarId);
        return toCalendarResponse(calendar);
    }

    /**
     * Gets upcoming periods for all active calendars.
     */
    @Transactional(readOnly = true)
    public Map<String, List<PayrollPeriodResponse>> getUpcomingPeriodsByCalendar(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(daysAhead);

        List<PayrollPeriod> upcomingPeriods = periodRepository.findAll(
            (Specification<PayrollPeriod>) (root, query, cb) ->
                cb.and(
                    cb.isTrue(root.get("calendar").get("active")),
                    cb.isFalse(root.get("closed")),
                    cb.between(root.get("paymentDate"), today, endDate)
                )
        );

        return upcomingPeriods
            .stream()
            .collect(
                Collectors.groupingBy(p -> p.getCalendar().getName(), Collectors.mapping(this::toPeriodResponse, Collectors.toList()))
            );
    }

    private LocalDate calculatePeriodEnd(LocalDate startDate, Frequency frequency) {
        return switch (frequency) {
            case WEEKLY -> startDate.plusDays(6);
            case BIWEEKLY -> startDate.plusDays(13);
            case SEMIMONTHLY -> {
                if (startDate.getDayOfMonth() <= 15) {
                    yield startDate.withDayOfMonth(15);
                } else {
                    yield YearMonth.from(startDate).atEndOfMonth();
                }
            }
            case MONTHLY -> YearMonth.from(startDate).atEndOfMonth();
        };
    }

    private LocalDate calculatePaymentDate(LocalDate periodEnd, int dayOffset) {
        LocalDate paymentDate = periodEnd.plusDays(dayOffset);
        // Skip weekends
        while (paymentDate.getDayOfWeek().getValue() > 5) {
            paymentDate = paymentDate.plusDays(1);
        }
        return paymentDate;
    }

    private String generatePeriodCode(PayrollCalendar calendar, LocalDate startDate) {
        String dateFormat =
            switch (calendar.getFrequency()) {
                case WEEKLY, BIWEEKLY -> startDate.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
                case MONTHLY, SEMIMONTHLY -> startDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            };

        return String.format(
            "%s-%s-%s",
            dateFormat,
            calendar.getFrequency().name(),
            calendar.getName().toUpperCase().replaceAll("\\s+", "-").substring(0, Math.min(10, calendar.getName().length()))
        );
    }

    private PayrollCalendarResponse toCalendarResponse(PayrollCalendar calendar) {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAhead = today.plusMonths(3);

        List<PayrollPeriod> upcomingPeriods = periodRepository.findAll(
            (Specification<PayrollPeriod>) (root, query, cb) -> {
                if (query != null) {
                    query.orderBy(cb.asc(root.get("startDate")));
                }
                return cb.and(
                    cb.equal(root.get("calendar").get("id"), calendar.getId()),
                    cb.greaterThanOrEqualTo(root.get("endDate"), today),
                    cb.lessThanOrEqualTo(root.get("startDate"), threeMonthsAhead)
                );
            }
        );

        int totalPeriods = (int) periodRepository.count(
            (Specification<PayrollPeriod>) (root, query, cb) -> cb.equal(root.get("calendar").get("id"), calendar.getId())
        );

        return new PayrollCalendarResponse(
            calendar.getId(),
            calendar.getName(),
            calendar.getFrequency(),
            calendar.getTimezone().getID(),
            calendar.getActive(),
            totalPeriods,
            upcomingPeriods
                .stream()
                .limit(5)
                .map(p ->
                    new PayrollCalendarResponse.PayrollPeriodSummary(
                        p.getId(),
                        p.getCode(),
                        p.getStartDate(),
                        p.getEndDate(),
                        p.getPaymentDate(),
                        p.isClosed()
                    )
                )
                .toList()
        );
    }

    private PayrollPeriodResponse toPeriodResponse(PayrollPeriod period) {
        PayrollCalendar calendar = period.getCalendar();

        // Check for approved runs
        // This would require PayrollRunRepository - simplified for now
        boolean hasApprovedRun = false;

        return new PayrollPeriodResponse(
            period.getId(),
            period.getCode(),
            calendar.getId(),
            calendar.getName(),
            period.getStartDate(),
            period.getEndDate(),
            period.getPaymentDate(),
            period.isClosed(),
            0, // payrollRunCount
            hasApprovedRun
        );
    }
}
