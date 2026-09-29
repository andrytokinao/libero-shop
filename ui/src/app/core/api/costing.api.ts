import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { MarginReport, ProductCost, SupplierPrice } from '../models';

/** Purchase costs and margins. The server refuses these to the counter roles. */
@Injectable({ providedIn: 'root' })
export class CostingApi {
  private readonly http = inject(HttpClient);

  productCosts(): Observable<ProductCost[]> {
    return this.http.get<ProductCost[]>(`${API_BASE_URL}/costing/products`);
  }

  /** What each supplier has charged for one product, cheapest on average first. */
  supplierPrices(productId: number): Observable<SupplierPrice[]> {
    return this.http.get<SupplierPrice[]>(`${API_BASE_URL}/costing/products/${productId}/suppliers`);
  }

  /** @param from, to ISO dates (yyyy-MM-dd), both inclusive; the server defaults to this month */
  margin(from?: string, to?: string): Observable<MarginReport> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<MarginReport>(`${API_BASE_URL}/costing/margin`, { params });
  }
}
