import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { CashRemittance, Payment, RemittanceStatus } from '../models';

@Injectable({ providedIn: 'root' })
export class RemittanceApi {
  private readonly http = inject(HttpClient);

  search(options: { status?: RemittanceStatus; mine?: boolean } = {}): Observable<CashRemittance[]> {
    let params = new HttpParams();
    if (options.status) {
      params = params.set('status', options.status);
    }
    if (options.mine) {
      params = params.set('mine', true);
    }
    return this.http.get<CashRemittance[]>(`${API_BASE_URL}/remittances`, { params });
  }

  /** What the calling agent has collected and not handed over yet. */
  cashInHand(): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${API_BASE_URL}/remittances/cash-in-hand`);
  }

  /** Brings cash to the desk: these orders' only, or everything held when none is given. */
  submit(invoiceIds: number[] = []): Observable<CashRemittance> {
    return this.http.post<CashRemittance>(`${API_BASE_URL}/remittances`, { invoiceIds });
  }

  confirm(remittanceId: number): Observable<CashRemittance> {
    return this.http.post<CashRemittance>(`${API_BASE_URL}/remittances/${remittanceId}/confirm`, {});
  }
}
