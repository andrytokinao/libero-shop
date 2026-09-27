import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CategoryNode,
  CategoryStock,
  CreateCategoryRequest,
  DeletedCategory,
  ImportFormat,
  Product,
  ProductImportPreview,
  ProductImportRequest,
  ProductImportResult,
  SimpleImportKind,
  SimpleImportPreview,
  SimpleImportRequest,
  SimpleImportResult,
  Supplier,
  UpdateCategoryRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  /**
   * @param categoryId a rayon **and everything below it** — picking "Boissons" returns the
   *   waters and the juices too, which is decided server-side so every screen agrees
   */
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

  suppliers(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(`${API_BASE_URL}/suppliers`);
  }

  stockByCategory(): Observable<CategoryStock[]> {
    return this.http.get<CategoryStock[]>(`${API_BASE_URL}/stock/by-category`);
  }

  // ------------------------------------------------------------------ categories

  /** The whole tree, depth-first, each row carrying its depth — see CategoryNode. */
  categories(): Observable<CategoryNode[]> {
    return this.http.get<CategoryNode[]>(`${API_BASE_URL}/categories`);
  }

  createCategory(request: CreateCategoryRequest): Observable<CategoryNode> {
    return this.http.post<CategoryNode>(`${API_BASE_URL}/categories`, request);
  }

  /** Renames, and moves when `parentId` differs — including to null, meaning a root. */
  updateCategory(id: number, request: UpdateCategoryRequest): Observable<CategoryNode> {
    return this.http.put<CategoryNode>(`${API_BASE_URL}/categories/${id}`, request);
  }

  /**
   * Removes a rayon.
   *
   * @param moveProductsTo where the goods go. Omit it to have the server refuse and say how
   *   many are in the way — which is how the screen learns what to ask before asking it.
   */
  deleteCategory(id: number, moveProductsTo?: number | null): Observable<DeletedCategory> {
    let params = new HttpParams();
    if (moveProductsTo != null) {
      params = params.set('moveProductsTo', moveProductsTo);
    }
    return this.http.delete<DeletedCategory>(`${API_BASE_URL}/categories/${id}`, { params });
  }

  // ---------------------------------------------------------------------- import

  /** The columns the server understands, so the dialog recommends them without repeating them. */
  importFormat(): Observable<ImportFormat> {
    return this.http.get<ImportFormat>(`${API_BASE_URL}/products/import/columns`);
  }

  /**
   * Reads the file and says what applying it would do. Writes nothing — so this may be called
   * again on the same file as often as the operator likes.
   *
   * @param content the file's text, already decoded by `readTextFile`
   */
  importPreview(content: string): Observable<ProductImportPreview> {
    return this.http.post<ProductImportPreview>(`${API_BASE_URL}/products/import/preview`, {
      content,
    });
  }

  /** Writes the ticked lines. The only call here that changes the catalogue. */
  importApply(request: ProductImportRequest): Observable<ProductImportResult> {
    return this.http.post<ProductImportResult>(`${API_BASE_URL}/products/import/apply`, request);
  }

  // ------------------------------------------------------- categories & suppliers

  /**
   * The two imports that are only a few text columns wide, behind one pair of methods.
   *
   * <p>Keyed by the same `resource` the preview answers with, so a caller passes the kind around
   * as data rather than picking a method — which is what lets one dialog component serve both
   * without knowing which it is looking at.
   */
  simpleImportPreview(
    resource: SimpleImportKind['resource'],
    content: string,
  ): Observable<SimpleImportPreview> {
    return this.http.post<SimpleImportPreview>(`${API_BASE_URL}/${resource}/import/preview`, {
      content,
    });
  }

  simpleImportApply(
    resource: SimpleImportKind['resource'],
    request: SimpleImportRequest,
  ): Observable<SimpleImportResult> {
    return this.http.post<SimpleImportResult>(
      `${API_BASE_URL}/${resource}/import/apply`,
      request,
    );
  }
}
