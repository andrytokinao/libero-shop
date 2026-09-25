import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { CreateSupplyRequest, StockOutput, Supply } from '../models';

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
}
