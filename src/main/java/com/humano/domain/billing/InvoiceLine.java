package com.humano.domain.billing;

import com.humano.domain.enumeration.billing.InvoiceLineKind;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One line of an {@link Invoice} — e.g. "Growth plan, monthly" for a period, or 3 packs of extra
 * storage at 25 each. Snapshotted when the invoice is issued: later plan or price changes never
 * alter it. {@code amount} is {@code quantity × unitPrice}, before any discount or tax.
 */
@Entity
@Table(name = "billing_invoice_line")
public class InvoiceLine {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Order of the line on the invoice. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    @NotNull
    private InvoiceLineKind kind;

    @Column(name = "description", nullable = false)
    @NotNull
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal amount;

    /** The service period billed, when the line is for one. */
    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    protected InvoiceLine() {}

    public InvoiceLine(
        InvoiceLineKind kind,
        String description,
        int quantity,
        BigDecimal unitPrice,
        Instant periodStart,
        Instant periodEnd
    ) {
        this.kind = kind;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public UUID getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public InvoiceLineKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvoiceLine other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
