import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { Category, CategoryStock, Product, Supplier } from '../models';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  products(options: { search?: string; categoryId?: number | null; lowStockOnly?: boolean } = {}):
    Observable<Product[]> {
    let params = new HttpParams();
    if (options.search?.trim()) {
      params = params.set('search', options.search.trim());
    }
    if (options.categoryId != null) {
      params = params.set('categoryId', options.categoryId);
    }
    if (options.lowStockOnly) {
      params = params.set('lowStockOnly', true);
    }
    return this.http.get<Product[]>(`${API_BASE_URL}/products`, { params });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${API_BASE_URL}/categories`);
  }

  suppliers(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(`${API_BASE_URL}/suppliers`);
  }

  stockByCategory(): Observable<CategoryStock[]> {
    return this.http.get<CategoryStock[]>(`${API_BASE_URL}/stock/by-category`);
  }
}
