package com.humano.service.billing;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.InvoiceLine;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.service.billing.InvoicePdfGenerator.InvoicePdfModel;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An invoice as a PDF document, for the customer and for the platform. The issuer printed on it is
 * configuration ({@code humano.billing.issuer.*}); the customer is the tenant with its billing
 * contact; the lines are those snapshotted when the invoice was issued.
 */
@Service
public class InvoiceDocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    /** The downloadable file. */
    public record PdfFile(String filename, byte[] bytes) {}

    private final InvoiceRepository invoiceRepository;
    private final TenantAdminEmailResolver contactResolver;
    private final InvoicePdfGenerator generator;
    private final TransactionTemplate masterTx;
    private final InvoicePdfModel.Issuer issuer;

    public InvoiceDocumentService(
        InvoiceRepository invoiceRepository,
        TenantAdminEmailResolver contactResolver,
        InvoicePdfGenerator generator,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Value("${humano.billing.issuer.name:Humano}") String issuerName,
        @Value("${humano.billing.issuer.address:}") String issuerAddress,
        @Value("${humano.billing.issuer.email:}") String issuerEmail,
        @Value("${humano.billing.issuer.website:}") String issuerWebsite,
        @Value("${humano.billing.issuer.tax-id:}") String issuerTaxId
    ) {
        this.invoiceRepository = invoiceRepository;
        this.contactResolver = contactResolver;
        this.generator = generator;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.masterTx.setReadOnly(true);
        this.issuer = new InvoicePdfModel.Issuer(
            issuerName,
            Arrays.stream(issuerAddress.split("\\|")).map(String::trim).filter(l -> !l.isEmpty()).toList(),
            blankToNull(issuerEmail),
            blankToNull(issuerWebsite),
            blankToNull(issuerTaxId)
        );
    }

    /**
     * The invoice as a PDF. With {@code requiredTenantId}, only an invoice of that tenant: any other
     * is refused, as if it did not belong to the caller.
     */
    public PdfFile pdf(UUID invoiceId, UUID requiredTenantId) {
        Draft draft = masterTx.execute(tx -> {
            Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> EntityNotFoundException.create("Invoice", invoiceId));
            if (requiredTenantId != null && !requiredTenantId.equals(invoice.getTenant().getId())) {
                throw new AccessDeniedException("Invoice does not belong to the current tenant");
            }
            return draft(invoice);
        });
        // The billing contact lives in the tenant's own database: read it outside the master transaction.
        String email = contactResolver.resolveBillingContact(draft.subdomain()).orElse(null);
        InvoicePdfModel model = draft.model();
        InvoicePdfModel.Customer c = model.customer();
        InvoicePdfModel withContact = new InvoicePdfModel(
            model.issuer(),
            model.number(),
            model.status(),
            model.statusLabel(),
            model.issueDate(),
            model.dueDate(),
            model.paidDate(),
            model.currency(),
            new InvoicePdfModel.Customer(c.name(), c.workspace(), c.country(), email),
            model.lines(),
            model.subtotal(),
            model.discount(),
            model.couponCode(),
            model.taxLabel(),
            model.tax(),
            model.total(),
            model.amountPaid(),
            model.amountDue(),
            model.notes()
        );
        return new PdfFile(model.number() + ".pdf", generator.generate(withContact));
    }

    private record Draft(InvoicePdfModel model, String subdomain) {}

    private Draft draft(Invoice invoice) {
        String currency = invoice.getCurrency() != null ? invoice.getCurrency() : "USD";
        NumberFormat money = money(currency);
        Tenant tenant = invoice.getTenant();

        List<InvoicePdfModel.Line> lines = invoice.getLines().isEmpty()
            // Issued before lines were recorded: one line for the whole amount.
            ? List.of(
                new InvoicePdfModel.Line(
                    invoice.getNotes() != null ? invoice.getNotes() : "Humano subscription",
                    null,
                    1,
                    money.format(invoice.getAmount()),
                    money.format(invoice.getAmount())
                )
            )
            : invoice.getLines().stream().map(l -> line(l, money)).toList();
        String notes = invoice.getLines().isEmpty() ? null : invoice.getNotes();

        BigDecimal discount = invoice.getDiscountAmount();
        boolean hasDiscount = discount != null && discount.signum() > 0;
        BigDecimal tax = invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = invoice.getTotalAmount();
        InvoiceStatus status = invoice.getStatus();
        BigDecimal paid = status == InvoiceStatus.PAID ? total : BigDecimal.ZERO;
        BigDecimal due = status == InvoiceStatus.PENDING || status == InvoiceStatus.OVERDUE ? total : BigDecimal.ZERO;

        InvoicePdfModel model = new InvoicePdfModel(
            issuer,
            invoice.getInvoiceNumber(),
            stamp(status),
            stampLabel(status),
            date(invoice.getIssueDate()),
            date(invoice.getDueDate()),
            date(invoice.getPaidDate()),
            currency,
            new InvoicePdfModel.Customer(
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getCountry() != null ? tenant.getCountry().getCountryName() : null,
                null
            ),
            lines,
            money.format(invoice.getAmount()),
            hasDiscount ? "-" + money.format(discount) : null,
            hasDiscount ? invoice.getCouponCode() : null,
            taxLabel(invoice.getTaxRate()),
            money.format(tax),
            money.format(total),
            paid.signum() > 0 ? "-" + money.format(paid) : null,
            money.format(due),
            notes
        );
        return new Draft(model, tenant.getSubdomain());
    }

    private static InvoicePdfModel.Line line(InvoiceLine l, NumberFormat money) {
        String period = l.getPeriodStart() != null && l.getPeriodEnd() != null
            ? date(l.getPeriodStart()) + " - " + date(l.getPeriodEnd())
            : null;
        return new InvoicePdfModel.Line(
            l.getDescription(),
            period,
            l.getQuantity(),
            money.format(l.getUnitPrice()),
            money.format(l.getAmount())
        );
    }

    /** "Tax (20%)", or "Tax" when the rate was not recorded. */
    static String taxLabel(BigDecimal rate) {
        if (rate == null || rate.signum() == 0) return "Tax";
        BigDecimal percent = rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return "Tax (" + percent.toPlainString() + "%)";
    }

    private static String stamp(InvoiceStatus status) {
        return switch (status) {
            case PAID -> "paid";
            case OVERDUE -> "overdue";
            case CANCELLED -> "void";
            case PENDING -> "due";
        };
    }

    private static String stampLabel(InvoiceStatus status) {
        return switch (status) {
            case PAID -> "Paid";
            case OVERDUE -> "Overdue";
            case CANCELLED -> "Void";
            case PENDING -> "Due";
        };
    }

    static NumberFormat money(String currency) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
        try {
            f.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException e) {
            // Unknown code: keep the default symbol rather than fail the document.
        }
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f;
    }

    private static String date(Instant instant) {
        return instant != null ? DATE.format(instant) : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
