package com.humano.domain.enumeration.billing;

/**
 * What an invoice line bills: the plan for a period, packs bought on top of it, or a charge the
 * platform entered by hand. Coupons only ever discount {@link #PLAN} lines.
 */
public enum InvoiceLineKind {
    PLAN,
    ADDON,
    MANUAL,
}
