import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { EXPECTED_STATUSES } from '../interceptors/error.interceptor';
import { API_BASE_URL } from './api.config';
import {
  CatalogExplore,
  CatalogPage,
  CategoryNode,
  CategoryStock,
  CreateCategoryRequest,
  CreateProductRequest,
  CreateSupplierRequest,
  DeletedCategory,
  ImportFormat,
  PackagingRequest,
  Product,
  ProductBrowse,
  ProductImportPreview,
  ProductImportRequest,
  ProductImportResult,
  ProductPage,
  ProductUnits,
  SimpleImportKind,
  SimpleImportPreview,
  SimpleImportRequest,
  SimpleImportResult,
  Supplier,
  UpdateCategoryRequest,
  UpdateProductRequest,
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

  /**
   * At most `limit` products matching every word of `query` — accents and case ignored, on the
   * name, the barcode or the rayon — within `categoryId` and the rayons below it. What the sale
   * screens use instead of {@link products}: the database filters and cuts, the catalogue never
   * travels whole.
   */
  searchProducts(query: string, categoryId: number | null = null, limit?: number):
    Observable<Product[]> {
    let params = new HttpParams().set('q', query.trim());
    if (categoryId != null) {
      params = params.set('categoryId', categoryId);
    }
    if (limit != null) {
      params = params.set('limit', limit);
    }
    return this.http.get<Product[]>(`${API_BASE_URL}/products/search`, { params });
  }

  /**
   * One page of what a sale screen browses — words, a rayon with everything below it, the
   * seller's favourites, any combination — by name, with the total for "Afficher plus".
   */
  browseProducts(browse: ProductBrowse): Observable<ProductPage> {
    let params = new HttpParams();
    if (browse.q?.trim()) {
      params = params.set('q', browse.q.trim());
    }
    if (browse.categoryId != null) {
      params = params.set('categoryId', browse.categoryId);
    }
    if (browse.favorites) {
      params = params.set('favorites', true);
    }
    if (browse.page) {
      params = params.set('page', browse.page);
    }
    if (browse.size != null) {
      params = params.set('size', browse.size);
    }
    return this.http.get<ProductPage>(`${API_BASE_URL}/products/browse`, { params });
  }

  /**
   * The catalogue as a file explorer: the folders (rayons) of where the grid is, then a page of
   * its products — see CatalogExplorerService.
   */
  explore(where: CatalogExplore): Observable<CatalogPage> {
    let params = new HttpParams();
    if (where.q?.trim()) {
      params = params.set('q', where.q.trim());
    }
    if (where.rayonId != null) {
      params = params.set('rayonId', where.rayonId);
    }
    if (where.all) {
      params = params.set('all', true);
    }
    if (where.favorites) {
      params = params.set('favorites', true);
    }
    if (where.page) {
      params = params.set('page', where.page);
    }
    if (where.size != null) {
      params = params.set('size', where.size);
    }
    return this.http.get<CatalogPage>(`${API_BASE_URL}/products/explore`, { params });
  }

  /**
   * A rayon's picture. Fetched rather than put in an `<img src>`: the token travels in a header,
   * which an image request cannot carry. `version` makes the address change with the picture, so
   * the browser may keep it for good. A picture gone meanwhile (404) is not worth a toast.
   */
  categoryImage(categoryId: number, version: number): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/categories/${categoryId}/image`, {
      params: { v: version },
      responseType: 'blob',
      context: new HttpContext().set(EXPECTED_STATUSES, [404]),
    });
  }

  /** Sets or replaces a rayon's picture; answers the rayon with its new `imageVersion`. */
  uploadCategoryImage(categoryId: number, image: Blob): Observable<CategoryNode> {
    const body = new FormData();
    body.append('file', image, 'rayon.jpg');
    return this.http.put<CategoryNode>(`${API_BASE_URL}/categories/${categoryId}/image`, body);
  }

  removeCategoryImage(categoryId: number): Observable<CategoryNode> {
    return this.http.delete<CategoryNode>(`${API_BASE_URL}/categories/${categoryId}/image`);
  }

  /** The rayons whose name holds every word of `query`, to offer them beside the products found. */
  searchCategories(query: string, limit?: number): Observable<CategoryNode[]> {
    let params = new HttpParams().set('q', query.trim());
    if (limit != null) {
      params = params.set('limit', limit);
    }
    return this.http.get<CategoryNode[]>(`${API_BASE_URL}/categories/search`, { params });
  }

  /** What a sale screen shows before anything is typed: the best sellers, topped up by name. */
  featuredProducts(limit?: number): Observable<Product[]> {
    const params = limit != null ? new HttpParams().set('limit', limit) : undefined;
    return this.http.get<Product[]>(`${API_BASE_URL}/products/featured`, { params });
  }

  /**
   * The product a scanned or typed code designates, matched whole — or null when none carries
   * it. An unknown code is an ordinary moment at the till, not a failure: the caller says so in
   * its own words, so only that 404 is kept from the error toast.
   */
  productByCode(code: string): Observable<Product | null> {
    return this.http
      .get<Product>(`${API_BASE_URL}/products/lookup`, {
        params: { code },
        context: new HttpContext().set(EXPECTED_STATUSES, [404]),
      })
      .pipe(
        catchError((error: HttpErrorResponse) =>
          error.status === 404 ? of(null) : throwError(() => error),
        ),
      );
  }

  // ------------------------------------------------------------------- sale units

  /** The base unit and the other units of one product. Every write below answers the same shape. */
  productUnits(productId: number): Observable<ProductUnits> {
    return this.http.get<ProductUnits>(`${API_BASE_URL}/products/${productId}/units`);
  }

  /** A product typed in by hand, with what is already on the shelf. Answers it as the lists show it. */
  createProduct(request: CreateProductRequest): Observable<Product> {
    return this.http.post<Product>(`${API_BASE_URL}/products`, request);
  }

  /** Corrects the sheet: name, price, barcode, rayon, purchase cost. Past tickets keep what they sold at. */
  updateProduct(productId: number, request: UpdateProductRequest): Observable<ProductUnits> {
    return this.http.put<ProductUnits>(`${API_BASE_URL}/products/${productId}`, request);
  }

  /** @param unit blank clears it, for a product counted in bare units */
  renameBaseUnit(productId: number, unit: string): Observable<ProductUnits> {
    return this.http.put<ProductUnits>(`${API_BASE_URL}/products/${productId}/units/base`, { unit });
  }

  addPackaging(productId: number, request: PackagingRequest): Observable<ProductUnits> {
    return this.http.post<ProductUnits>(`${API_BASE_URL}/products/${productId}/units`, request);
  }

  updatePackaging(productId: number, packagingId: number, request: PackagingRequest):
    Observable<ProductUnits> {
    return this.http.put<ProductUnits>(
      `${API_BASE_URL}/products/${productId}/units/${packagingId}`,
      request,
    );
  }

  removePackaging(productId: number, packagingId: number): Observable<ProductUnits> {
    return this.http.delete<ProductUnits>(
      `${API_BASE_URL}/products/${productId}/units/${packagingId}`,
    );
  }

  suppliers(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(`${API_BASE_URL}/suppliers`);
  }

  /** Refused (409) when a supplier of that name, however written, is already on the list. */
  createSupplier(request: CreateSupplierRequest): Observable<Supplier> {
    return this.http.post<Supplier>(`${API_BASE_URL}/suppliers`, request);
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

  /** The same preview for an Excel workbook, sent as the file itself: its text is not ours to decode. */
  importPreviewSpreadsheet(file: File): Observable<ProductImportPreview> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ProductImportPreview>(`${API_BASE_URL}/products/import/preview`, body);
  }

  /** The model workbook to fill in: recommended headers, two example lines, a help sheet. */
  importTemplate(): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/products/import/template`, { responseType: 'blob' });
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
