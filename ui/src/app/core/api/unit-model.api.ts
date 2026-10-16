import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  ApplyUnitModelRequest,
  CreateUnitModelRequest,
  ProductUnits,
  UnitModel,
  UnitModelUnitRequest,
} from '../models';

/** The models of units (/api/unit-models), and putting one on a product. */
@Injectable({ providedIn: 'root' })
export class UnitModelApi {
  private readonly http = inject(HttpClient);

  models(): Observable<UnitModel[]> {
    return this.http.get<UnitModel[]>(`${API_BASE_URL}/unit-models`);
  }

  create(request: CreateUnitModelRequest): Observable<UnitModel> {
    return this.http.post<UnitModel>(`${API_BASE_URL}/unit-models`, request);
  }

  rename(modelId: number, name: string): Observable<UnitModel> {
    return this.http.put<UnitModel>(`${API_BASE_URL}/unit-models/${modelId}`, { name });
  }

  /** The products it was applied to keep their units: they were copied. */
  delete(modelId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/unit-models/${modelId}`);
  }

  addUnit(modelId: number, request: UnitModelUnitRequest): Observable<UnitModel> {
    return this.http.post<UnitModel>(`${API_BASE_URL}/unit-models/${modelId}/units`, request);
  }

  removeUnit(modelId: number, unitId: number): Observable<UnitModel> {
    return this.http.delete<UnitModel>(`${API_BASE_URL}/unit-models/${modelId}/units/${unitId}`);
  }

  /** Ticked units of a model onto a product, all or none. Answers the product's units. */
  applyToProduct(productId: number, request: ApplyUnitModelRequest): Observable<ProductUnits> {
    return this.http.post<ProductUnits>(`${API_BASE_URL}/products/${productId}/units/from-model`, request);
  }
}
