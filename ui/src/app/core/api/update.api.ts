import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';

/** What the hidden update page shows and acts on. Mirrors the server's UpdateStatusResponse. */
export interface UpdateStatus {
  currentVersion: string;
  canApply: boolean;
  unavailableReason: string | null;
  canRollback: boolean;
  automaticEnabled: boolean;
  urlConfigured: boolean;
  checkTime: string;
  requireConfirmation: boolean;
}

/**
 * Updating the application. Super-admin only, and the page has no menu entry — reached at
 * /admin/mise-a-jour, like the licence page. Applying or rolling back restarts the server, so
 * those calls answer first and the screen then waits for it to come back.
 */
@Injectable({ providedIn: 'root' })
export class UpdateApi {
  private readonly http = inject(HttpClient);

  status(): Observable<UpdateStatus> {
    return this.http.get<UpdateStatus>(`${API_BASE_URL}/update/status`);
  }

  /** Sends a .lsu file; the server verifies it, replaces its code and restarts. */
  upload(file: File): Observable<{ message: string; version: string }> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<{ message: string; version: string }>(
      `${API_BASE_URL}/update/upload`,
      body,
    );
  }

  rollback(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_BASE_URL}/update/rollback`, {});
  }
}
