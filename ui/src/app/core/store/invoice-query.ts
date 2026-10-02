import { InvoiceQuery } from '../api/invoice.api';
import { Invoice } from '../models';

/** Who is asking and when: what `mine` and `todayOnly` are resolved against. */
export interface QueryContext {
  readonly userId: number | null;
  /** The current local day, `yyyy-MM-dd`. */
  readonly today: string;
}

/**
 * Whether an order belongs to the answer of `GET /api/invoices` for this query — the server's
 * InvoiceService.search, said again in the browser so that an order pushed over the socket joins
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
  if (query.todayOnly && invoice.invoiceDate.slice(0, 10) !== context.today) {
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
