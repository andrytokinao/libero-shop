import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CreateSaleRequest,
  DeliveryResult,
  DeliveryStatus,
  Invoice,
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

  deliver(invoiceId: number): Observable<DeliveryResult> {
    return this.http.post<DeliveryResult>(`${API_BASE_URL}/invoices/${invoiceId}/deliver`, {});
  }

  print(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE_URL}/invoices/${invoiceId}/print`, {});
  }
}
