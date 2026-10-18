package com.humano.service.billing;

import com.humano.service.payroll.PayslipPdfGenerator;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders an invoice from the Thymeleaf XHTML template {@code templates/billing/invoice.html} to a
 * PDF via OpenHTMLtoPDF, like {@link PayslipPdfGenerator}. Takes a flat, pre-formatted
 * {@link InvoicePdfModel} so the template never touches JPA entities.
 */
@Service
public class InvoicePdfGenerator {

    private final SpringTemplateEngine templateEngine;

    public InvoicePdfGenerator(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] generate(InvoicePdfModel model) {
        Context ctx = new Context();
        ctx.setVariable("model", model);
        String html;
        try {
            html = PayslipPdfGenerator.sanitizeForXmlAndBase14(templateEngine.process("billing/invoice", ctx));
        } catch (RuntimeException e) {
            throw new PayslipPdfGenerator.PdfGenerationException("Thymeleaf rendering failed for invoice " + model.number(), e);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new PayslipPdfGenerator.PdfGenerationException("OpenHTMLtoPDF rendering failed for invoice " + model.number(), e);
        }
    }

    /**
     * What the invoice shows, every amount and date already formatted. Optional parts are null:
     * the template leaves them out.
     *
     * @param status   {@code paid}, {@code due}, {@code overdue} or {@code void} — the stamp and its colour
     * @param amountDue what is still owed, "0" formatted when paid or void
     */
    public record InvoicePdfModel(
        Issuer issuer,
        String number,
        String status,
        String statusLabel,
        String issueDate,
        String dueDate,
        String paidDate,
        String currency,
        Customer customer,
        List<Line> lines,
        String subtotal,
        String discount,
        String couponCode,
        String taxLabel,
        String tax,
        String total,
        String amountPaid,
        String amountDue,
        String notes
    ) {
        public record Issuer(String name, List<String> addressLines, String email, String website, String taxId) {}

        public record Customer(String name, String workspace, String country, String email) {}

        /** {@code period} is e.g. "Oct 16, 2026 - Nov 15, 2026", null when the line is not for one. */
        public record Line(String description, String period, int quantity, String unitPrice, String amount) {}
    }
}
