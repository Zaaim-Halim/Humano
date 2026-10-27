package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.ReviewCadence;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.hr.ReviewCalendarSettings;
import com.humano.dto.hr.workflow.requests.UpdateReviewCalendarRequest;
import com.humano.dto.hr.workflow.responses.ReviewCalendarResponse;
import com.humano.repository.hr.ReviewCalendarSettingsRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.service.payroll.OrganizationSettingsService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The company's review calendar ({@link ReviewCalendarSettings}) and the next cycle it proposes.
 *
 * <p>Review periods follow the cadence from the month the review year starts: a year, or two
 * halves. The proposal is the period running today, or the one that just ended if it ended within
 * {@value #LATE_REVIEW_DAYS} days and was never reviewed (a year-end review held in January). A
 * period that already has a cycle (same period or same name) is skipped for the next one. The cycle
 * opens {@code opensDaysBeforePeriodEnd} days before its period ends (today if that has passed),
 * and the phase deadlines follow one another by their lengths.
 *
 * <p>{@link #get()} never persists; HR creates every cycle from the proposal themselves.
 */
@Service
@Transactional("tenantTransactionManager")
public class ReviewCalendarService {

    /** How long after a period ends it is still proposed when it has no cycle. */
    static final int LATE_REVIEW_DAYS = 60;

    private final ReviewCalendarSettingsRepository repository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final OrganizationSettingsService organizationSettingsService;

    public ReviewCalendarService(
        ReviewCalendarSettingsRepository repository,
        ReviewCycleRepository reviewCycleRepository,
        OrganizationSettingsService organizationSettingsService
    ) {
        this.repository = repository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.organizationSettingsService = organizationSettingsService;
    }

    /** The calendar (defaults until saved) with the next proposed cycle. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewCalendarResponse get() {
        ReviewCalendarSettings saved = repository.findAll().stream().findFirst().orElse(null);
        return toResponse(saved != null ? saved : new ReviewCalendarSettings(), saved != null);
    }

    /** Replace the calendar, creating it on the first save. Existing cycles are not changed. */
    public ReviewCalendarResponse update(UpdateReviewCalendarRequest request) {
        ReviewCalendarSettings settings = repository.findAll().stream().findFirst().orElseGet(ReviewCalendarSettings::new);
        settings.setCadence(request.cadence());
        settings.setYearStartMonth(request.yearStartMonth());
        settings.setOpensDaysBeforePeriodEnd(request.opensDaysBeforePeriodEnd());
        settings.setSelfAssessmentDays(request.selfAssessmentDays());
        settings.setManagerReviewDays(request.managerReviewDays());
        settings.setCalibrationDays(request.calibrationDays());
        settings.setFeedbackDays(request.feedbackDays());
        return toResponse(repository.save(settings), true);
    }

    private ReviewCalendarResponse toResponse(ReviewCalendarSettings s, boolean customized) {
        LocalDate today = LocalDate.now(organizationSettingsService.zone());
        ReviewCalendarResponse.CycleProposal next = propose(
            s,
            today,
            (proposal, name) ->
                reviewCycleRepository.existsByTypeAndReviewPeriodStartAndReviewPeriodEnd(
                    ReviewCycleType.REGULAR,
                    proposal.reviewPeriodStart(),
                    proposal.reviewPeriodEnd()
                ) ||
                reviewCycleRepository.findByName(name).isPresent()
        );
        return new ReviewCalendarResponse(
            s.getCadence(),
            s.getYearStartMonth(),
            s.getOpensDaysBeforePeriodEnd(),
            s.getSelfAssessmentDays(),
            s.getManagerReviewDays(),
            s.getCalibrationDays(),
            s.getFeedbackDays(),
            customized,
            next
        );
    }

    /**
     * The next cycle the calendar proposes on {@code today}.
     *
     * @param reviewed whether a proposal (with its name) already has a cycle
     */
    static ReviewCalendarResponse.CycleProposal propose(
        ReviewCalendarSettings s,
        LocalDate today,
        BiPredicate<ReviewCalendarResponse.CycleProposal, String> reviewed
    ) {
        int months = months(s.getCadence());
        LocalDate start = periodContaining(today, s.getYearStartMonth(), months);

        LocalDate previous = start.minusMonths(months);
        ReviewCalendarResponse.CycleProposal late = proposal(s, previous, months, today);
        if (ChronoUnit.DAYS.between(late.reviewPeriodEnd(), today) <= LATE_REVIEW_DAYS && !reviewed.test(late, late.name())) {
            return late;
        }

        ReviewCalendarResponse.CycleProposal candidate = proposal(s, start, months, today);
        // A few periods ahead at most; beyond that HR is planning far ahead and edits the dates themselves.
        for (int i = 0; i < 4 && reviewed.test(candidate, candidate.name()); i++) {
            start = start.plusMonths(months);
            candidate = proposal(s, start, months, today);
        }
        return candidate;
    }

    private static ReviewCalendarResponse.CycleProposal proposal(
        ReviewCalendarSettings s,
        LocalDate periodStart,
        int months,
        LocalDate today
    ) {
        LocalDate periodEnd = periodStart.plusMonths(months).minusDays(1);
        LocalDate opens = periodEnd.minusDays(s.getOpensDaysBeforePeriodEnd());
        if (opens.isBefore(today)) {
            opens = today;
        }
        LocalDate self = opens.plusDays(s.getSelfAssessmentDays());
        LocalDate manager = self.plusDays(s.getManagerReviewDays());
        LocalDate calibration = s.getCalibrationDays() > 0 ? manager.plusDays(s.getCalibrationDays()) : null;
        LocalDate feedback = (calibration != null ? calibration : manager).plusDays(s.getFeedbackDays());
        return new ReviewCalendarResponse.CycleProposal(
            name(s, periodStart, months),
            periodStart,
            periodEnd,
            opens,
            feedback,
            self,
            manager,
            calibration,
            feedback
        );
    }

    /** "2026 Annual Review", "2026 Mid-year Review", or "2026/27 Year-end Review" for a review year not starting in January. */
    private static String name(ReviewCalendarSettings s, LocalDate periodStart, int months) {
        LocalDate yearStart = periodContaining(periodStart, s.getYearStartMonth(), 12);
        String year = s.getYearStartMonth() == 1
            ? String.valueOf(yearStart.getYear())
            : yearStart.getYear() + "/" + String.format("%02d", (yearStart.getYear() + 1) % 100);
        if (months == 12) {
            return year + " Annual Review";
        }
        return year + (periodStart.equals(yearStart) ? " Mid-year Review" : " Year-end Review");
    }

    /** First day of the period of {@code months} months, aligned on {@code yearStartMonth}, that contains {@code day}. */
    private static LocalDate periodContaining(LocalDate day, int yearStartMonth, int months) {
        LocalDate start = LocalDate.of(day.getYear(), yearStartMonth, 1);
        if (start.isAfter(day)) {
            start = start.minusYears(1);
        }
        while (!start.plusMonths(months).isAfter(day)) {
            start = start.plusMonths(months);
        }
        return start;
    }

    private static int months(ReviewCadence cadence) {
        return cadence == ReviewCadence.SEMI_ANNUAL ? 6 : 12;
    }
}
