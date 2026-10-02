import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';

/** What the signed-in seller pinned — mirrors FavoritesResponse. Newest first. */
export interface Favorites {
  productIds: number[];
  categoryIds: number[];
}

/**
 * The signed-in seller's own favourites. Every call answers the whole list; pinning and unpinning
 * are idempotent, so a double tap on a star is never an error.
 */
@Injectable({ providedIn: 'root' })
export class FavoriteApi {
  private readonly http = inject(HttpClient);

  mine(): Observable<Favorites> {
    return this.http.get<Favorites>(`${API_BASE_URL}/favorites`);
  }

  pin(kind: 'products' | 'categories', id: number): Observable<Favorites> {
    return this.http.put<Favorites>(`${API_BASE_URL}/favorites/${kind}/${id}`, null);
  }

  unpin(kind: 'products' | 'categories', id: number): Observable<Favorites> {
    return this.http.delete<Favorites>(`${API_BASE_URL}/favorites/${kind}/${id}`);
  }
}
