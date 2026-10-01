import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { ShopSettings, UpdateShopSettingsRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);

  current(): Observable<ShopSettings> {
    return this.http.get<ShopSettings>(`${API_BASE_URL}/settings`);
  }

  /** Super-admin only. Answers what was kept, switches the server had to reconcile included. */
  update(request: UpdateShopSettingsRequest): Observable<ShopSettings> {
    return this.http.put<ShopSettings>(`${API_BASE_URL}/settings`, request);
  }
}
