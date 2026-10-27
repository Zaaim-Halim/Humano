package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.hr.ReviewCycle;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.dto.hr.workflow.responses.ReviewForm;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.payroll.PayslipPdfGenerator.PdfGenerationException;
import com.humano.service.tenant.TenantService;
import com.humano.tenancy.TenantIdResolver;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * A shared performance review as a PDF ({@code templates/hr/performance-review.html}), for the
 * employee's records or the personnel file.
 *
 * <p>It always prints the <b>employee's view</b> — whoever downloads it — so it never holds the
 * reviewer's rating before calibration, potential, promotion or probation recommendations, calibration
 * notes, the sign-off or colleagues' feedback. Only shared reviews are exported, and every export is
 * written to the audit log.
 *
 * <p>Review text is free text in any language, so it is set in Liberation Sans (bundled with PDFBox,
 * SIL Open Font License), which covers Latin, Greek and Cyrillic — not the base-14 Helvetica the
 * payslip uses, which lacks letters such as ő, ł or Cyrillic.
 */
@Service
public class ReviewPdfService {

    private static final Logger log = LoggerFactory.getLogger(ReviewPdfService.class);
    private static final String FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final Map<Integer, String> RATINGS = Map.of(
        1,
        "Doesn't meet expectations",
        2,
        "Partially meets expectations",
        3,
        "Meets expectations",
        4,
        "Exceeds expectations",
        5,
        "Outstanding"
    );
    private static final Map<GoalAchievement, String> ACHIEVEMENTS = Map.of(
        GoalAchievement.NOT_MET,
        "Not met",
        GoalAchievement.PARTIALLY_MET,
        "Partially met",
        GoalAchievement.MET,
        "Met",
        GoalAchievement.EXCEEDED,
        "Exceeded"
    );

    /** A rendered review: the file name to offer and the PDF. */
    public record ReviewPdf(String filename, byte[] content) {}

    private final PerformanceReviewCycleService cycleService;
    private final ReviewCycleRepository cycleRepository;
    private final SpringTemplateEngine templateEngine;
    private final AuditEventService auditEventService;
    private final TenantService tenantService;
    private final TenantIdResolver tenantIdResolver;

    public ReviewPdfService(
        PerformanceReviewCycleService cycleService,
        ReviewCycleRepository cycleRepository,
        SpringTemplateEngine templateEngine,
        AuditEventService auditEventService,
        TenantService tenantService,
        TenantIdResolver tenantIdResolver
    ) {
        this.cycleService = cycleService;
        this.cycleRepository = cycleRepository;
        this.templateEngine = templateEngine;
        this.auditEventService = auditEventService;
        this.tenantService = tenantService;
        this.tenantIdResolver = tenantIdResolver;
    }

    /** Renders a shared review for someone who may read it, and logs the export. */
    @Transactional("tenantTransactionManager")
    public ReviewPdf export(UUID reviewId, UUID actorId, boolean hrView) {
        PerformanceReviewResponse review = cycleService.getSharedReviewAsEmployeeSees(reviewId, actorId, hrView);
        ReviewCycle cycle = cycleRepository.findById(review.cycleId()).orElse(null);
        ReviewPdfModel model = toModel(review, cycle, employerName());
        byte[] pdf = render(model);
        auditEventService.record(
            "PERFORMANCE_REVIEW_EXPORTED",
            "PerformanceReview",
            reviewId.toString(),
            Map.of("employeeId", review.employeeId().toString(), "byEmployee", Objects.equals(review.employeeId(), actorId))
        );
        return new ReviewPdf(filename(review), pdf);
    }

    /** Renders the model to PDF bytes. */
    byte[] render(ReviewPdfModel model) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("model", model);
        String html;
        try {
            // OpenHTMLtoPDF parses XML: it takes no HTML5 doctype.
            html = templateEngine.process("hr/performance-review", ctx).replaceFirst("(?i)^\\s*<!doctype[^>]*>", "");
        } catch (RuntimeException e) {
            throw new PdfGenerationException("Thymeleaf rendering failed for a performance review", e);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> ReviewPdfService.class.getResourceAsStream(FONT), "Liberation Sans");
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new PdfGenerationException("OpenHTMLtoPDF rendering failed for a performance review", e);
        }
    }

    /** The employee's view of the review, flattened for the template. */
    static ReviewPdfModel toModel(PerformanceReviewResponse r, ReviewCycle cycle, String employerName) {
        ReviewForm form = r.form();
        List<ReviewPdfModel.Competency> competencies = List.of();
        List<ReviewPdfModel.Goal> goals = List.of();
        List<ReviewPdfModel.Answer> employeeAnswers = List.of();
        List<ReviewPdfModel.Answer> reviewerAnswers = List.of();
        if (form != null) {
            Map<UUID, ReviewForm.CompetencyRating> ratings = form
                .ratings()
                .stream()
                .collect(Collectors.toMap(ReviewForm.CompetencyRating::competencyId, Function.identity(), (a, b) -> a));
            competencies = form
                .competencies()
                .stream()
                .map(c -> {
                    ReviewForm.CompetencyRating rating = ratings.get(c.id());
                    Integer value = rating != null ? rating.rating() : null;
                    return new ReviewPdfModel.Competency(
                        c.name(),
                        value,
                        RATINGS.get(value),
                        rating != null ? blankToNull(rating.comment()) : null
                    );
                })
                .toList();
            goals = form
                .goals()
                .stream()
                .map(g ->
                    new ReviewPdfModel.Goal(
                        g.title(),
                        g.successMeasure(),
                        g.weight(),
                        g.achievement() != null ? ACHIEVEMENTS.get(g.achievement()) : null,
                        blankToNull(g.comment())
                    )
                )
                .toList();
            Map<UUID, String> answers = form
                .answers()
                .stream()
                .filter(a -> a.answer() != null && !a.answer().isBlank())
                .collect(Collectors.toMap(ReviewForm.Answer::questionId, ReviewForm.Answer::answer, (a, b) -> a));
            employeeAnswers = answersFor(form, answers, ReviewQuestionAudience.EMPLOYEE);
            reviewerAnswers = answersFor(form, answers, ReviewQuestionAudience.REVIEWER);
        }

        PerformanceReviewResponse.SelfAssessment self = r.selfAssessment();
        PerformanceReviewResponse.ManagerAssessment manager = r.managerAssessment();
        PerformanceReviewResponse.Acknowledgement ack = r.acknowledgement();
        return new ReviewPdfModel(
            blankToNull(employerName),
            r.employeeName(),
            r.employeeJobTitle(),
            r.departmentName(),
            r.reviewerName(),
            r.cycleName(),
            cycle != null ? date(cycle.getReviewPeriodStart()) : null,
            cycle != null ? date(cycle.getReviewPeriodEnd()) : null,
            date(r.sharedAt()),
            r.rating(),
            RATINGS.get(r.rating()),
            form != null ? form.templateName() : null,
            competencies,
            goals,
            self != null
                ? new ReviewPdfModel.Self(
                    blankToNull(self.achievements()),
                    blankToNull(self.challenges()),
                    blankToNull(self.developmentGoals()),
                    blankToNull(self.comments()),
                    date(self.submittedAt())
                )
                : null,
            employeeAnswers,
            manager != null
                ? new ReviewPdfModel.Manager(
                    blankToNull(manager.strengths()),
                    blankToNull(manager.areasForImprovement()),
                    blankToNull(manager.developmentRecommendations()),
                    date(manager.submittedAt())
                )
                : null,
            reviewerAnswers,
            blankToNull(r.comments()),
            blankToNull(r.feedbackNotes()),
            ack != null
                ? new ReviewPdfModel.Acknowledgement(
                    date(ack.acknowledgedAt()),
                    blankToNull(ack.comment()),
                    ack.disputed(),
                    blankToNull(ack.resolution()),
                    date(ack.resolvedAt()),
                    ack.resolvedByName()
                )
                : null,
            date(Instant.now())
        );
    }

    private static List<ReviewPdfModel.Answer> answersFor(ReviewForm form, Map<UUID, String> answers, ReviewQuestionAudience audience) {
        return form
            .questions()
            .stream()
            .filter(q -> q.audience() == audience && answers.containsKey(q.id()))
            .map(q -> new ReviewPdfModel.Answer(q.text(), answers.get(q.id())))
            .toList();
    }

    /** {@code Performance review - Jane Doe - 2026 Annual Review.pdf}, in plain ASCII. */
    static String filename(PerformanceReviewResponse r) {
        String name = "Performance review - " + r.employeeName() + (r.cycleName() != null ? " - " + r.cycleName() : "");
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^A-Za-z0-9 ._-]", "")
            .replaceAll("\\s+", " ")
            .trim();
        return (ascii.length() > 150 ? ascii.substring(0, 150).trim() : ascii) + ".pdf";
    }

    /** The company name for the letterhead; the PDF still renders if it can't be resolved. */
    private String employerName() {
        try {
            return tenantService.getTenantById(tenantIdResolver.requireCurrentTenantId()).name();
        } catch (RuntimeException e) {
            log.warn("Could not resolve the employer name for a review PDF: {}", e.getMessage());
            return null;
        }
    }

    private static String date(LocalDate date) {
        return date != null ? DATE.format(date) : null;
    }

    private static String date(Instant instant) {
        return instant != null ? DATE.format(instant.atZone(ZoneId.systemDefault())) : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * What the template prints — the employee's view only, flattened so the template never touches
     * an entity. Text fields are {@code null} when empty, so their sections are left out.
     */
    public record ReviewPdfModel(
        String employerName,
        String employeeName,
        String jobTitle,
        String department,
        String reviewerName,
        String cycleName,
        String periodStart,
        String periodEnd,
        String sharedOn,
        Integer rating,
        String ratingLabel,
        String templateName,
        List<Competency> competencies,
        List<Goal> goals,
        Self self,
        List<Answer> employeeAnswers,
        Manager manager,
        List<Answer> reviewerAnswers,
        String overallComments,
        String meetingNotes,
        Acknowledgement acknowledgement,
        String generatedOn
    ) {
        public record Competency(String name, Integer rating, String ratingLabel, String comment) {}

        public record Goal(String title, String successMeasure, Integer weight, String achievement, String comment) {}

        public record Answer(String question, String answer) {}

        public record Self(String achievements, String challenges, String developmentGoals, String comments, String submittedOn) {}

        public record Manager(String strengths, String areasForImprovement, String developmentRecommendations, String submittedOn) {}

        public record Acknowledgement(
            String acknowledgedOn,
            String comment,
            boolean disputed,
            String resolution,
            String resolvedOn,
            String resolvedByName
        ) {}
    }
}
