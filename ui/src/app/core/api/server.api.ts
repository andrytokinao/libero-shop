import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { ServerConnection } from '../models';

/** How phones reach this server. The server's identity is probed by ServerConfig, not here. */
@Injectable({ providedIn: 'root' })
export class ServerApi {
  private readonly http = inject(HttpClient);

  /** Administrators only: the server's local addresses and the published APK. */
  connection(): Observable<ServerConnection> {
    return this.http.get<ServerConnection>(`${API_BASE_URL}/server/connection`);
  }
}
