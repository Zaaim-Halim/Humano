package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.service.payroll.PayslipPdfGenerator.PayslipPdfModel;
import com.humano.service.payroll.PayslipPdfGenerator.PayslipPdfModel.LineItem;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** A payslip renders to a PDF from its template. */
class PayslipPdfGeneratorTest {

    private final PayslipPdfGenerator generator = new PayslipPdfGenerator(engine());

    @Test
    void rendersAPayslipWithItsLinesAndNet() throws IOException {
        PayslipPdfModel model = new PayslipPdfModel(
            "PS-2026-10-001",
            UUID.randomUUID(),
            "Ava Romano",
            "EMP-001",
            "Engineering",
            "Developer",
            "2026-10",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 10, 31),
            "USD",
            List.of(new LineItem("BASE", "Base salary", null, null, new BigDecimal("5000.00"), "Monthly base — prorated")),
            List.of(new LineItem("TAX", "Income tax", null, null, new BigDecimal("800.00"), null)),
            List.of(),
            new BigDecimal("5000.00"),
            new BigDecimal("800.00"),
            new BigDecimal("4200.00"),
            null,
            null,
            null,
            null,
            null,
            null,
            "2026-10-31 12:00",
            "Acme Corp"
        );

        byte[] pdf = generator.generate(model);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(new PDFTextStripper().getText(doc))
                .contains("PS-2026-10-001")
                .contains("Ava Romano")
                .contains("Base salary")
                .contains("USD 4,200.00")
                .contains("October 2026")
                .contains("Page 1 of 1");
        }
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
