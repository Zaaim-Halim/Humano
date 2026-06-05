package com.humano.service.payroll;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders a single payslip from a Thymeleaf XHTML template
 * ({@code templates/payroll/payslip.html}) to a PDF byte array via OpenHTMLtoPDF
 * (PDFBox backend). One bean for the whole app — stateless, thread-safe.
 *
 * <p>This service deliberately takes a flat {@link PayslipPdfModel} record rather than JPA
 * entities so the template never touches a lazy-loaded association and the renderer can
 * run outside an active transaction.
 */
@Service
public class PayslipPdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(PayslipPdfGenerator.class);

    private final SpringTemplateEngine templateEngine;

    public PayslipPdfGenerator(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Renders the payslip to PDF bytes. Throws {@link PdfGenerationException} on any
     * rendering failure (Thymeleaf parse error, OpenHTMLtoPDF layout error, PDF write
     * failure); callers should let it bubble — there is no useful per-line recovery.
     */
    public byte[] generate(PayslipPdfModel model) {
        Context ctx = new Context();
        ctx.setVariable("model", model);
        String html;
        try {
            html = templateEngine.process("payroll/payslip", ctx);
        } catch (RuntimeException e) {
            throw new PdfGenerationException("Thymeleaf rendering failed for payslip " + model.payslipNumber(), e);
        }
        html = sanitizeForXmlAndBase14(html);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            byte[] bytes = baos.toByteArray();
            log.debug("Rendered payslip {} -> {} bytes", model.payslipNumber(), bytes.length);
            return bytes;
        } catch (Exception e) {
            throw new PdfGenerationException("OpenHTMLtoPDF rendering failed for payslip " + model.payslipNumber(), e);
        }
    }

    /**
     * Post-processes Thymeleaf-rendered HTML so OpenHTMLtoPDF's strict XML parser and the
     * base-14 (WinAnsi) default font can both consume it:
     *
     * <ul>
     *   <li>Thymeleaf's HTML5 serializer emits named entities ({@code &mdash;},
     *       {@code &middot;}, {@code &nbsp;}, &hellip;) that aren't declared in XML, so
     *       OpenHTMLtoPDF's TRaX parser throws "entity not declared". We replace the ones
     *       the payroll explain strings actually produce with ASCII or numeric equivalents.</li>
     *   <li>The base-14 Helvetica font ships with WinAnsi encoding, which lacks several
     *       typographic glyphs that show up in {@code PayrollProcessingService.explain}
     *       (em-dash U+2014, en-dash U+2013, middle dot U+00B7, non-breaking space U+00A0).
     *       Replacing them with ASCII keeps the renderer's default font path working with
     *       no embedded-font dependency. Embedding a Unicode-capable font would be a
     *       separate, larger change.</li>
     * </ul>
     *
     * <p>This is a deliberate scope limit: explain strings are operator-readable text,
     * not legally typeset content, so ASCII normalization is acceptable. If a future
     * task surfaces these strings in a UI that needs proper typography, embed a font
     * (e.g. DejaVu Sans) instead of expanding this normalizer.
     */
    static String sanitizeForXmlAndBase14(String html) {
        if (html == null || html.isEmpty()) return html;
        return html
            // Named HTML entities -> ASCII (chosen so the parser AND the font both accept).
            .replace("&mdash;", "-")
            .replace("&ndash;", "-")
            .replace("&middot;", "|")
            .replace("&nbsp;", " ")
            .replace("&hellip;", "...")
            .replace("&laquo;", "<<")
            .replace("&raquo;", ">>")
            // Same code points as literal UTF-8 (covers explain-string content).
            .replace("—", "-") // em-dash
            .replace("–", "-") // en-dash
            .replace("·", "|") // middle dot
            .replace(" ", " ") // non-breaking space
            .replace("−", "-") // minus sign
            .replace("‘", "'") // left single quote
            .replace("’", "'") // right single quote
            .replace("“", "\"") // left double quote
            .replace("”", "\""); // right double quote
    }

    /**
     * Flat data model passed to the Thymeleaf template. Built by
     * {@link PayslipService#buildPdfModel(java.util.UUID)} from the persisted
     * {@code Payslip} + {@code PayrollResult} + {@code PayrollLine} graph.
     *
     * <p>Money fields are pre-formatted {@link BigDecimal}s (2dp scale guaranteed by the
     * calc pipeline's invariant I6); the template renders them as plain text without
     * additional rounding.
     *
     * <p>Reporting fields are populated only when the parent {@code PayrollRun} carries a
     * reporting currency (P3.4). When null the template's reporting block is hidden via
     * {@code th:if}.
     */
    public record PayslipPdfModel(
        String payslipNumber,
        UUID runId,
        String employeeName,
        String employeeCode,
        String department,
        String position,
        String periodCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate paymentDate,
        String currencyCode,
        List<LineItem> earnings,
        List<LineItem> deductions,
        List<LineItem> employerCharges,
        BigDecimal gross,
        BigDecimal totalDeductions,
        BigDecimal net,
        String reportingCurrencyCode,
        BigDecimal reportingGross,
        BigDecimal reportingTotalDeductions,
        BigDecimal reportingNet,
        BigDecimal exchangeRate,
        LocalDate exchangeRateDate,
        String generatedAt
    ) {
        public record LineItem(String code, String name, BigDecimal quantity, BigDecimal rate, BigDecimal amount, String explain) {}
    }

    /** Thrown when PDF rendering fails at any stage (Thymeleaf parse, layout, write). */
    public static class PdfGenerationException extends RuntimeException {

        public PdfGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
