package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.hr.ReviewCycle;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.dto.hr.workflow.responses.ReviewForm;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.tenant.TenantService;
import com.humano.tenancy.TenantIdResolver;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** The review PDF: the employee's view, in any language, with nothing that stays with HR and management. */
class ReviewPdfServiceTest {

    private final ReviewPdfService service = new ReviewPdfService(
        mock(PerformanceReviewCycleService.class),
        mock(ReviewCycleRepository.class),
        engine(),
        mock(AuditEventService.class),
        mock(TenantService.class),
        mock(TenantIdResolver.class)
    );

    @Test
    void printsTheSharedReviewInAnyLanguage() throws IOException {
        UUID competency = UUID.randomUUID();
        UUID employeeQuestion = UUID.randomUUID();
        UUID reviewerQuestion = UUID.randomUUID();
        ReviewForm form = new ReviewForm(
            "Annual review",
            List.of(new ReviewForm.FormCompetency(competency, "Ownership", null, CompetencyCategory.CORE, List.of())),
            List.of(
                new ReviewForm.FormQuestion(employeeQuestion, ReviewQuestionAudience.EMPLOYEE, "What are you most proud of?", null, false),
                new ReviewForm.FormQuestion(reviewerQuestion, ReviewQuestionAudience.REVIEWER, "What should they keep doing?", null, false)
            ),
            List.of(
                new ReviewForm.Answer(employeeQuestion, "Запуск нового продукта"),
                new ReviewForm.Answer(reviewerQuestion, "Mentoring the team")
            ),
            List.of(new ReviewForm.CompetencyRating(competency, 4, "Takes problems end to end")),
            List.of(
                new ReviewForm.FormGoal(
                    UUID.randomUUID(),
                    "Ship the onboarding",
                    "Live for all new hires",
                    60,
                    LocalDate.of(2026, 6, 30),
                    GoalStatus.COMPLETED,
                    100,
                    GoalAchievement.EXCEEDED,
                    "A month early"
                )
            )
        );
        PerformanceReviewResponse review = new PerformanceReviewResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Zsófia Kőváry",
            "Senior Analyst",
            "Finance",
            UUID.randomUUID(),
            "Łukasz Wójcik",
            LocalDate.of(2026, 11, 1),
            "A strong year overall.",
            4,
            UUID.randomUUID(),
            "2026 Annual Review",
            ReviewCycleType.REGULAR,
            ReviewCyclePhase.FEEDBACK_DELIVERY,
            null,
            PerformanceReviewStatus.SHARED,
            new PerformanceReviewResponse.SelfAssessment("Shipped the onboarding", null, "Public speaking", null, Instant.now()),
            new PerformanceReviewResponse.ManagerAssessment(
                null,
                "Clear communicator",
                "Delegation",
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
            ),
            "Agreed to lead the Q1 planning.",
            Instant.now(),
            new PerformanceReviewResponse.Acknowledgement(Instant.now(), "Thanks, fair review.", false, null, null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            form
        );
        ReviewCycle cycle = new ReviewCycle();
        cycle.setReviewPeriodStart(LocalDate.of(2026, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(2026, 12, 31));

        byte[] pdf = service.render(ReviewPdfService.toModel(review, cycle, "Acme Kft."));

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(new PDFTextStripper().getText(doc))
                .contains("Acme Kft.")
                .contains("Zsófia Kőváry")
                .contains("Łukasz Wójcik")
                .contains("Exceeds expectations")
                .contains("Ship the onboarding")
                .contains("Exceeded")
                .contains("Ownership")
                .contains("Takes problems end to end")
                .contains("Clear communicator")
                .contains("Mentoring the team")
                .contains("Запуск нового продукта")
                .contains("Agreed to lead the Q1 planning.")
                .contains("Thanks, fair review.")
                .contains("January 1, 2026 - December 31, 2026")
                .doesNotContain("Not acknowledged");
        }
    }

    @Test
    void theFileNameIsPlainAscii() {
        PerformanceReviewResponse review = mock(PerformanceReviewResponse.class);
        org.mockito.Mockito.when(review.employeeName()).thenReturn("Zsófia Kőváry");
        org.mockito.Mockito.when(review.cycleName()).thenReturn("2026 Annual Review / H2");

        assertThat(ReviewPdfService.filename(review)).isEqualTo("Performance review - Zsofia Kovary - 2026 Annual Review H2.pdf");
    }

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
