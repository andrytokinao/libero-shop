import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CreateStockAdjustmentRequest,
  CreateDeliveryRequest,
  CreateInventoryRequest,
  CreateSupplyRequest,
  ProductMovementPage,
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

  /** Everything that moved one product's stock — receipts, sales, corrections — newest first. */
  productMovements(productId: number, page: number): Observable<ProductMovementPage> {
    return this.http.get<ProductMovementPage>(
      `${API_BASE_URL}/stock/products/${productId}/movements`,
      { params: { page } },
    );
  }

  registerSupply(request: CreateSupplyRequest): Observable<Supply> {
    return this.http.post<Supply>(`${API_BASE_URL}/stock/supplies`, request);
  }

  /** An opposite correction puts the stock back; the receipt stays, marked cancelled. */
  cancelSupply(supplyId: number): Observable<StockAdjustment> {
    return this.http.post<StockAdjustment>(`${API_BASE_URL}/stock/supplies/${supplyId}/cancel`, {});
  }

  cancelAdjustment(adjustmentId: number): Observable<StockAdjustment> {
    return this.http.post<StockAdjustment>(`${API_BASE_URL}/stock/adjustments/${adjustmentId}/cancel`, {});
  }

  /** Every line of the note, or none: a refused line leaves the stock as it was. */
  registerDelivery(request: CreateDeliveryRequest): Observable<Supply[]> {
    return this.http.post<Supply[]>(`${API_BASE_URL}/stock/deliveries`, request);
  }

  /** A delivery note taken back whole: every receipt or none. */
  cancelDelivery(supplyIds: readonly number[]): Observable<StockAdjustment[]> {
    return this.http.post<StockAdjustment[]>(`${API_BASE_URL}/stock/deliveries/cancel`, { supplyIds });
  }

  /** A rayon counted in one go: answers the corrections written, one per product found different. */
  registerInventory(request: CreateInventoryRequest): Observable<StockAdjustment[]> {
    return this.http.post<StockAdjustment[]>(`${API_BASE_URL}/stock/inventories`, request);
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
