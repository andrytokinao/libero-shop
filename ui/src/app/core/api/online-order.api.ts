import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SILENT_ERRORS } from '../interceptors/error.interceptor';
import { API_BASE_URL } from './api.config';
import { DiningTable, PublicMenu, PublicOrder, PublicOrderRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class OnlineOrderApi {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------ the customer (no account)

  /** Silent: the page explains a refusal itself (off the Wi-Fi, code withdrawn). */
  menu(token: string): Observable<PublicMenu> {
    return this.http.get<PublicMenu>(`${API_BASE_URL}/public/tables/${token}/menu`, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  order(token: string, request: PublicOrderRequest): Observable<PublicOrder> {
    return this.http.post<PublicOrder>(`${API_BASE_URL}/public/tables/${token}/orders`, request);
  }

  /** Polled in the background: its failures are the page's to handle, not a toast's. */
  status(token: string, invoiceNumber: string): Observable<PublicOrder> {
    return this.http.get<PublicOrder>(`${API_BASE_URL}/public/tables/${token}/orders/${invoiceNumber}`, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  // ------------------------------------------------------------ the tables (super-admin)

  tables(): Observable<DiningTable[]> {
    return this.http.get<DiningTable[]>(`${API_BASE_URL}/tables`);
  }

  createTable(name: string): Observable<DiningTable> {
    return this.http.post<DiningTable>(`${API_BASE_URL}/tables`, { name });
  }

  /** Withdraws the printed QR code: the old link stops working. */
  regenerate(id: number): Observable<DiningTable> {
    return this.http.post<DiningTable>(`${API_BASE_URL}/tables/${id}/regenerate`, {});
  }

  deleteTable(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/tables/${id}`);
  }
}
