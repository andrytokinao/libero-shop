import { InvoiceQuery } from '../api/invoice.api';
import { DeliveryStatus, Invoice, PaymentStatus } from '../models';

/**
 * How far back a list goes when the screen names no period — the server's
 * InvoiceService.HISTORY_DAYS. Keep the two in step.
 */
export const HISTORY_DAYS = 30;

/** Who is asking and when: what `mine`, `todayOnly` and the default period are resolved against. */
export interface QueryContext {
  readonly userId: number | null;
  /** The current local day, `yyyy-MM-dd`. */
  readonly today: string;
  /** The first day of the default period: {@link HISTORY_DAYS} days before today. */
  readonly historyStart: string;
}

/** Still waiting for someone — the server's InvoiceAccess.isOpen. */
function isOpen(invoice: Invoice): boolean {
  return (
    invoice.paymentStatus === PaymentStatus.UNPAID ||
    invoice.paymentStatus === PaymentStatus.COLLECTED ||
    invoice.paymentStatus === PaymentStatus.REMITTED ||
    invoice.deliveryStatus === DeliveryStatus.PENDING ||
    invoice.deliveryStatus === DeliveryStatus.IN_PROGRESS
  );
}

/** The period of the query: today, the days asked for, or the default with what is still open. */
function inPeriod(invoice: Invoice, query: InvoiceQuery, context: QueryContext): boolean {
  const day = invoice.invoiceDate.slice(0, 10);
  if (query.todayOnly) {
    return day === context.today;
  }
  if (query.from || query.to) {
    return (!query.from || day >= query.from) && (!query.to || day <= query.to);
  }
  return day >= context.historyStart || isOpen(invoice);
}

/**
 * Whether an order belongs to the answer of `GET /api/invoices` for this query — the server's
 * InvoiceService.searchFor, said again in the browser so that an order pushed over the socket joins
 * the lists it belongs to and leaves the others. Kept in step with that method.
 */
export function matchesInvoiceQuery(
  invoice: Invoice,
  query: InvoiceQuery,
  context: QueryContext,
): boolean {
  if (query.mine && invoice.sale.seller.id !== context.userId) {
    return false;
  }
  if (query.paymentStatus && invoice.paymentStatus !== query.paymentStatus) {
    return false;
  }
  if (query.deliveryStatus && invoice.deliveryStatus !== query.deliveryStatus) {
    return false;
  }
  if (!inPeriod(invoice, query, context)) {
    return false;
  }
  const needle = query.search?.trim().toLowerCase() ?? '';
  return (
    needle === '' ||
    [invoice.invoiceNumber, invoice.clientName, invoice.sale.seller.fullName].some((text) =>
      text.toLowerCase().includes(needle),
    )
  );
}

/** The server's order: newest first — `order by invoiceDate desc, id desc`. */
export function newestInvoiceFirst(a: Invoice, b: Invoice): number {
  if (a.invoiceDate !== b.invoiceDate) {
    return a.invoiceDate < b.invoiceDate ? 1 : -1;
  }
  return b.id - a.id;
}
