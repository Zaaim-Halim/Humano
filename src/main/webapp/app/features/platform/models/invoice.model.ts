import { AuditFields } from 'app/core/api';

import { InvoiceStatus } from './enums/invoice-status.enum';

/** `GET /api/billing/invoices` (list) and `/{id}` (detail). */
export interface Invoice extends AuditFields {
  id: string;
  invoiceNumber: string;
  tenantId: string;
  tenantName: string | null;
  subscriptionId: string | null;
  amount: number;
  taxAmount: number | null;
  totalAmount: number;
  status: InvoiceStatus;
  issueDate: string | null;
  dueDate: string | null;
  paidDate: string | null;
  paymentCount: number;
}

export interface CreateInvoiceRequest {
  tenantId: string;
  subscriptionId: string;
  invoiceNumber: string;
  amount: number;
  taxAmount?: number;
  /** ISO instant. */
  dueDate: string;
  /** Optional coupon code applied before tax. */
  couponCode?: string;
}
