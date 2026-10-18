package com.humano.service.billing;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.InvoiceLine;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.InvoiceLineKind;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.CreateInvoiceRequest;
import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing invoices.
 * Handles CRUD operations and invoice lifecycle management.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingTaxResolver taxResolver;
    private final CouponService couponService;

    public InvoiceService(
        InvoiceRepository invoiceRepository,
        TenantRepository tenantRepository,
        SubscriptionRepository subscriptionRepository,
        BillingTaxResolver taxResolver,
        CouponService couponService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.taxResolver = taxResolver;
        this.couponService = couponService;
    }

    /**
     * Create a new invoice.
     *
     * @param request the invoice creation request
     * @return the created invoice response
     */
    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
        log.debug("Request to create Invoice: {}", request);

        Tenant tenant = tenantRepository
            .findById(request.tenantId())
            .orElseThrow(() -> EntityNotFoundException.create("Tenant", request.tenantId()));

        Subscription subscription = subscriptionRepository
            .findById(request.subscriptionId())
            .orElseThrow(() -> EntityNotFoundException.create("Subscription", request.subscriptionId()));

        Instant issueDate = Instant.now();

        // P4.5: if the request carries a coupon code, validate + redeem and compute the
        // discount BEFORE tax. The discount lowers the taxable base (standard practice:
        // tax applies on the price actually paid, not on the sticker price).
        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponCode = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            CouponService.CouponApplication applied = couponService.applyToAmount(request.couponCode(), request.amount());
            discountAmount = applied.discountAmount();
            couponCode = applied.coupon().getCode();
        }
        BigDecimal taxableSubtotal = request.amount().subtract(discountAmount);

        // P4.1: when the caller didn't supply taxAmount, look up the country rate via
        // BillingTaxResolver. An explicit caller-supplied value still wins — admins
        // creating manual / adjusted invoices may want to override (e.g. a credit-note
        // negative-tax row, or a tax-exempt scenario).
        BigDecimal taxAmount;
        BigDecimal taxRate;
        if (request.taxAmount() != null) {
            taxAmount = request.taxAmount();
            taxRate = null; // explicit override; no canonical rate to record
        } else {
            BillingTaxResolver.TaxResult tax = taxResolver.resolve(
                tenant.getCountry(),
                subscription.getSubscriptionPlan(),
                taxableSubtotal,
                issueDate.atZone(java.time.ZoneOffset.UTC).toLocalDate()
            );
            taxAmount = tax.amount();
            taxRate = tax.rate();
        }
        BigDecimal totalAmount = taxableSubtotal.add(taxAmount);

        Invoice invoice = new Invoice();
        invoice.setTenant(tenant);
        invoice.setSubscription(subscription);
        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setAmount(request.amount());
        invoice.setDiscountAmount(discountAmount.signum() > 0 ? discountAmount : null);
        invoice.setCouponCode(couponCode);
        invoice.setTaxAmount(taxAmount);
        invoice.setTaxRate(taxRate);
        invoice.setTotalAmount(totalAmount);
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(request.dueDate());
        String description = request.description() != null && !request.description().isBlank()
            ? request.description().trim()
            : "Manual charge";
        invoice.addLine(new InvoiceLine(InvoiceLineKind.MANUAL, description, 1, request.amount(), null, null));

        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Created invoice with ID: {}", savedInvoice.getId());

        return mapToResponse(savedInvoice);
    }

    /**
     * Get an invoice by ID.
     *
     * @param id the ID of the invoice
     * @return the invoice response
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(UUID id) {
        log.debug("Request to get Invoice by ID: {}", id);

        return invoiceRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Invoice", id));
    }

    /**
     * Get all invoices with pagination.
     *
     * @param pageable pagination information
     * @return page of invoice responses
     */
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getAllInvoices(Pageable pageable) {
        log.debug("Request to get all Invoices");

        return invoiceRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get invoices by tenant.
     *
     * @param tenantId the tenant ID
     * @return list of invoice responses
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByTenant(UUID tenantId) {
        log.debug("Request to get Invoices by Tenant: {}", tenantId);

        return invoiceRepository.findByTenantId(tenantId).stream().map(this::mapToResponse).toList();
    }

    /**
     * Mark an invoice as paid.
     *
     * @param id the ID of the invoice to mark as paid
     * @return the updated invoice response
     */
    @Transactional
    public InvoiceResponse markAsPaid(UUID id) {
        log.debug("Request to mark Invoice as paid: {}", id);

        return invoiceRepository
            .findById(id)
            .map(invoice -> {
                if (invoice.getStatus() == InvoiceStatus.PAID) {
                    return mapToResponse(invoice); // idempotent
                }
                requireOpen(invoice, "mark as paid");
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setPaidDate(Instant.now());
                return mapToResponse(invoiceRepository.save(invoice));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Invoice", id));
    }

    /**
     * Mark an invoice as overdue.
     *
     * @param id the ID of the invoice to mark as overdue
     * @return the updated invoice response
     */
    @Transactional
    public InvoiceResponse markAsOverdue(UUID id) {
        log.debug("Request to mark Invoice as overdue: {}", id);

        return invoiceRepository
            .findById(id)
            .map(invoice -> {
                requireOpen(invoice, "mark as overdue");
                invoice.setStatus(InvoiceStatus.OVERDUE);
                return mapToResponse(invoiceRepository.save(invoice));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Invoice", id));
    }

    /**
     * Void an unpaid invoice: it is no longer owed but stays on record as {@code CANCELLED}.
     * Paid invoices are never voided or deleted — refund the payment instead.
     *
     * @param id the ID of the invoice to void
     * @return the voided invoice
     */
    @Transactional
    public InvoiceResponse voidInvoice(UUID id) {
        log.debug("Request to void Invoice: {}", id);
        Invoice invoice = invoiceRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("Invoice", id));
        requireOpen(invoice, "void");
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return mapToResponse(invoiceRepository.save(invoice));
    }

    /** Only an open invoice (pending or overdue) can change state. */
    private static void requireOpen(Invoice invoice, String action) {
        if (invoice.getStatus() != InvoiceStatus.PENDING && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new BadRequestAlertException(
                "Cannot " + action + " an invoice that is " + invoice.getStatus().name().toLowerCase(),
                "invoice",
                "invoicenotopen"
            );
        }
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getTenant().getId(),
            invoice.getTenant().getName(),
            invoice.getSubscription().getId(),
            invoice.getAmount(),
            invoice.getTaxAmount(),
            invoice.getTotalAmount(),
            invoice.getCurrency(),
            invoice.getStatus(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getPaidDate(),
            invoice.getPayments() != null ? invoice.getPayments().size() : 0,
            invoice.getCreatedBy(),
            invoice.getCreatedDate(),
            invoice.getLastModifiedBy(),
            invoice.getLastModifiedDate()
        );
    }
}
