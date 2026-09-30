import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { MobileUpdateInfo, ServerConnection } from '../models';

/** How phones reach this server. The server's identity is probed by ServerConfig, not here. */
@Injectable({ providedIn: 'root' })
export class ServerApi {
  private readonly http = inject(HttpClient);

  /** Public: the server's addresses (shop network, Internet) and the published APK. */
  connection(): Observable<ServerConnection> {
    return this.http.get<ServerConnection>(`${API_BASE_URL}/server/connection`);
  }

  /** Public: the mobile pages this server carries, for the app to update itself. */
  mobileUpdate(): Observable<MobileUpdateInfo> {
    return this.http.get<MobileUpdateInfo>(`${API_BASE_URL}/mobile/update`);
  }
}
