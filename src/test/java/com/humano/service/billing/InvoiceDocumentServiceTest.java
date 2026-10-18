package com.humano.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.InvoiceLine;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.billing.InvoiceLineKind;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.InvoiceRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** An invoice downloads as a PDF showing its lines, discount, tax and what is due. */
class InvoiceDocumentServiceTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final TenantAdminEmailResolver contacts = mock(TenantAdminEmailResolver.class);
    private InvoiceDocumentService service;

    private final Tenant tenant = new Tenant();
    private final Invoice invoice = new Invoice();

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        service = new InvoiceDocumentService(
            invoices,
            contacts,
            new InvoicePdfGenerator(engine),
            mock(PlatformTransactionManager.class),
            "Humano",
            "1 Example Street | Casablanca",
            "billing@example.com",
            "",
            ""
        );

        tenant.setId(UUID.randomUUID());
        tenant.setName("Acme Corp");
        tenant.setSubdomain("acme");
        tenant.setCountry(CountryCode.MA);
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        Instant start = Instant.parse("2026-10-16T00:00:00Z");
        invoice.setId(UUID.randomUUID());
        invoice.setTenant(tenant);
        invoice.setSubscription(subscription);
        invoice.setInvoiceNumber("INV-20380-00042");
        invoice.setIssueDate(start);
        invoice.setDueDate(start.plus(30, ChronoUnit.DAYS));
        invoice.setCurrency("USD");
        invoice.setStatus(InvoiceStatus.PENDING);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(contacts.resolveBillingContact(any())).thenReturn(Optional.of("owner@acme.test"));
    }

    @Test
    void printsThePlanAndAddonLinesWithDiscountAndTax() throws IOException {
        Instant start = invoice.getIssueDate();
        Instant end = invoice.getDueDate();
        invoice.addLine(new InvoiceLine(InvoiceLineKind.PLAN, "Enterprise plan, billed monthly", 1, new BigDecimal("499"), start, end));
        invoice.addLine(new InvoiceLine(InvoiceLineKind.ADDON, "Extra storage, 100 GB pack", 3, new BigDecimal("25"), start, end));
        invoice.setAmount(new BigDecimal("574"));
        invoice.setDiscountAmount(new BigDecimal("49.90"));
        invoice.setCouponCode("SAVE10");
        invoice.setTaxRate(new BigDecimal("0.2000"));
        invoice.setTaxAmount(new BigDecimal("104.82"));
        invoice.setTotalAmount(new BigDecimal("628.92"));

        InvoiceDocumentService.PdfFile file = service.pdf(invoice.getId(), tenant.getId());

        assertThat(file.filename()).isEqualTo("INV-20380-00042.pdf");
        String text = text(file.bytes());
        assertThat(text)
            .contains("Enterprise plan, billed monthly")
            .contains("Extra storage, 100 GB pack")
            .contains("$75.00")
            .contains("Discount (SAVE10)")
            .contains("Tax (20%)")
            .contains("$628.92")
            .contains("Acme Corp")
            .contains("owner@acme.test")
            .contains("Oct 16, 2026 - Nov 15, 2026")
            .contains("Page 1 of 1")
            .contains("billing@example.com");
    }

    @Test
    void anInvoiceIssuedBeforeLinesExistedPrintsOneLine() throws IOException {
        invoice.setAmount(new BigDecimal("99"));
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal("99"));
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidDate(invoice.getIssueDate().plus(2, ChronoUnit.DAYS));

        String text = text(service.pdf(invoice.getId(), null).bytes());

        assertThat(text).contains("Humano subscription").contains("Paid").contains("$99.00");
    }

    @Test
    void anotherTenantsInvoiceIsRefused() {
        assertThatThrownBy(() -> service.pdf(invoice.getId(), UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void taxLabelShowsTheRecordedRate() {
        assertThat(InvoiceDocumentService.taxLabel(new BigDecimal("0.2000"))).isEqualTo("Tax (20%)");
        assertThat(InvoiceDocumentService.taxLabel(new BigDecimal("0.0550"))).isEqualTo("Tax (5.5%)");
        assertThat(InvoiceDocumentService.taxLabel(null)).isEqualTo("Tax");
    }

    private static String text(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
