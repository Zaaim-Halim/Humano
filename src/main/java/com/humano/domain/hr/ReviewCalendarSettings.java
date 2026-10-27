package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.ReviewCadence;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The company's review calendar — a <b>singleton per tenant schema</b>, created on the first save.
 * It says when review periods run (cadence and the month the review year starts), when a cycle
 * opens relative to the end of its period, and how long each phase lasts. New cycles are proposed
 * from it; HR still creates and starts every cycle.
 */
@Entity
@Table(name = "review_calendar_settings")
public class ReviewCalendarSettings extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", nullable = false, length = 20)
    private ReviewCadence cadence = ReviewCadence.ANNUAL;

    /** Month the review year starts, 1–12 (1 = calendar year). */
    @Column(name = "year_start_month", nullable = false)
    private int yearStartMonth = 1;

    /** Days before the end of the review period that its cycle opens. */
    @Column(name = "opens_days_before_period_end", nullable = false)
    private int opensDaysBeforePeriodEnd = 14;

    @Column(name = "self_assessment_days", nullable = false)
    private int selfAssessmentDays = 14;

    @Column(name = "manager_review_days", nullable = false)
    private int managerReviewDays = 14;

    /** Days of calibration; 0 when the company doesn't calibrate. */
    @Column(name = "calibration_days", nullable = false)
    private int calibrationDays = 7;

    @Column(name = "feedback_days", nullable = false)
    private int feedbackDays = 14;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReviewCadence getCadence() {
        return cadence;
    }

    public void setCadence(ReviewCadence cadence) {
        this.cadence = cadence;
    }

    public int getYearStartMonth() {
        return yearStartMonth;
    }

    public void setYearStartMonth(int yearStartMonth) {
        this.yearStartMonth = yearStartMonth;
    }

    public int getOpensDaysBeforePeriodEnd() {
        return opensDaysBeforePeriodEnd;
    }

    public void setOpensDaysBeforePeriodEnd(int opensDaysBeforePeriodEnd) {
        this.opensDaysBeforePeriodEnd = opensDaysBeforePeriodEnd;
    }

    public int getSelfAssessmentDays() {
        return selfAssessmentDays;
    }

    public void setSelfAssessmentDays(int selfAssessmentDays) {
        this.selfAssessmentDays = selfAssessmentDays;
    }

    public int getManagerReviewDays() {
        return managerReviewDays;
    }

    public void setManagerReviewDays(int managerReviewDays) {
        this.managerReviewDays = managerReviewDays;
    }

    public int getCalibrationDays() {
        return calibrationDays;
    }

    public void setCalibrationDays(int calibrationDays) {
        this.calibrationDays = calibrationDays;
    }

    public int getFeedbackDays() {
        return feedbackDays;
    }

    public void setFeedbackDays(int feedbackDays) {
        this.feedbackDays = feedbackDays;
    }

    @Override
    public String toString() {
        return "ReviewCalendarSettings{id=" + id + ", cadence=" + cadence + ", yearStartMonth=" + yearStartMonth + "}";
    }
}
