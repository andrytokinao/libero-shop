import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CreateStockAdjustmentRequest,
  CreateSupplyRequest,
  StockAdjustment,
  StockOutput,
  Supply,
} from '../models';

@Injectable({ providedIn: 'root' })
export class StockApi {
  private readonly http = inject(HttpClient);

  supplies(): Observable<Supply[]> {
    return this.http.get<Supply[]>(`${API_BASE_URL}/stock/supplies`);
  }

  outputs(search?: string): Observable<StockOutput[]> {
    let params = new HttpParams();
    if (search?.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<StockOutput[]>(`${API_BASE_URL}/stock/outputs`, { params });
  }

  registerSupply(request: CreateSupplyRequest): Observable<Supply> {
    return this.http.post<Supply>(`${API_BASE_URL}/stock/supplies`, request);
  }

  /** The corrections made by hand, newest first. */
  adjustments(): Observable<StockAdjustment[]> {
    return this.http.get<StockAdjustment[]>(`${API_BASE_URL}/stock/adjustments`);
  }

  /** Breakage, loss or a shelf counted; the server works out and answers the difference. */
  registerAdjustment(request: CreateStockAdjustmentRequest): Observable<StockAdjustment> {
    return this.http.post<StockAdjustment>(`${API_BASE_URL}/stock/adjustments`, request);
  }
}
