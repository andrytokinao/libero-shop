import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CancelInvoiceRequest,
  CreateSaleRequest,
  DeliveryResult,
  DeliveryStatus,
  Invoice,
  PayInvoiceRequest,
  PaymentStatus,
} from '../models';

export interface InvoiceQuery {
  /** Restrict to the caller's own invoices — the server resolves "mine", not the client. */
  mine?: boolean;
  paymentStatus?: PaymentStatus;
  deliveryStatus?: DeliveryStatus;
  todayOnly?: boolean;
  search?: string;
}

@Injectable({ providedIn: 'root' })
export class InvoiceApi {
  private readonly http = inject(HttpClient);

  search(query: InvoiceQuery = {}): Observable<Invoice[]> {
    let params = new HttpParams();
    if (query.mine) {
      params = params.set('mine', true);
    }
    if (query.paymentStatus) {
      params = params.set('paymentStatus', query.paymentStatus);
    }
    if (query.deliveryStatus) {
      params = params.set('deliveryStatus', query.deliveryStatus);
    }
    if (query.todayOnly) {
      params = params.set('todayOnly', true);
    }
    if (query.search?.trim()) {
      params = params.set('search', query.search.trim());
    }
    return this.http.get<Invoice[]>(`${API_BASE_URL}/invoices`, { params });
  }

  byId(id: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${API_BASE_URL}/invoices/${id}`);
  }

  /** Cash-desk checkout. The seller comes from the session, never from this payload. */
  createSale(request: CreateSaleRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/sales`, request);
  }

  /** @param collect false: served, the customer pays at the till */
  /** "Je m'en occupe": the order becomes the caller's to prepare. */
  take(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/take`, {});
  }

  /** Back to the queue — by whoever took it, or by the depot's manager. */
  release(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/release`, {});
  }

  deliver(invoiceId: number, collect = true): Observable<DeliveryResult> {
    return this.http.post<DeliveryResult>(`${API_BASE_URL}/invoices/${invoiceId}/deliver`, {}, {
      params: { collect },
    });
  }

  /** An order taker takes the cash of an unpaid order, to bring to the till later. */
  collect(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/collect`, {});
  }

  /** Settles an unpaid order at the till. */
  pay(invoiceId: number, request: PayInvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/pay`, request);
  }

  /** Cancels an unpaid order; its goods return to stock unless it was already handed over. */
  cancel(invoiceId: number, request: CancelInvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/cancel`, request);
  }

  print(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/print`, {});
  }
}
