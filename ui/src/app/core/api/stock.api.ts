import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CreateStockAdjustmentRequest,
  CreateSupplyRequest,
  StockAdjustment,
  StockOutputPage,
  Supply,
} from '../models';

@Injectable({ providedIn: 'root' })
export class StockApi {
  private readonly http = inject(HttpClient);

  supplies(): Observable<Supply[]> {
    return this.http.get<Supply[]>(`${API_BASE_URL}/stock/supplies`);
  }

  /**
   * One page of the outputs, newest first. The list grows by a row per line sold, so it is
   * never fetched whole: the server searches, cuts and adds up.
   *
   * @param size how many rows; the server's default when left out
   */
  outputs(search: string, page: number, size?: number): Observable<StockOutputPage> {
    let params = new HttpParams().set('page', page);
    if (search.trim()) {
      params = params.set('search', search.trim());
    }
    if (size != null) {
      params = params.set('size', size);
    }
    return this.http.get<StockOutputPage>(`${API_BASE_URL}/stock/outputs`, { params });
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
