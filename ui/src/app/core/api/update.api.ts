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

/** The outcome of looking at the configured update URL. Mirrors UpdateCheckResponse. */
export interface UpdateCheck {
  available: boolean;
  latestVersion: string;
  releasedOn: string | null;
  notes: string | null;
  currentVersion: string;
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

  /** Looks at the configured URL for a newer version. Installs nothing. */
  check(): Observable<UpdateCheck> {
    return this.http.post<UpdateCheck>(`${API_BASE_URL}/update/check`, {});
  }

  /** Downloads and installs the latest version from the configured URL, then restarts. */
  online(): Observable<{ message: string; version: string }> {
    return this.http.post<{ message: string; version: string }>(`${API_BASE_URL}/update/online`, {});
  }

  rollback(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_BASE_URL}/update/rollback`, {});
  }

  /** This screen agrees to be cut off for a pending update. */
  confirm(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/update/confirm`, {});
  }

  /** This screen refuses the cut now; the update is left for later. */
  decline(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/update/decline`, {});
  }
}

/** What the server broadcasts around a maintenance update, on the `maintenance` topic. */
export interface MaintenanceNotice {
  kind: 'CONFIRM_REQUEST' | 'APPLYING' | 'CANCELLED';
  version: string | null;
  message: string;
}
