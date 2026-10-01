import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { BackupConfig, BackupItem, BackupOverview } from '../models';

/** The database's backups. Super-admin only, like everything that touches the whole shop. */
@Injectable({ providedIn: 'root' })
export class BackupApi {
  private readonly http = inject(HttpClient);

  overview(): Observable<BackupOverview> {
    return this.http.get<BackupOverview>(`${API_BASE_URL}/backups`);
  }

  backupNow(): Observable<BackupItem> {
    return this.http.post<BackupItem>(`${API_BASE_URL}/backups/now`, {});
  }

  updateSettings(config: BackupConfig): Observable<BackupConfig> {
    return this.http.put<BackupConfig>(`${API_BASE_URL}/backups/settings`, config);
  }

  /** As a blob: the token travels in a header, which a plain link cannot carry. */
  download(fileName: string): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/backups/${encodeURIComponent(fileName)}/file`, {
      responseType: 'blob',
    });
  }

  /** Answers, then the server restarts on the restored database. */
  restore(fileName: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${API_BASE_URL}/backups/${encodeURIComponent(fileName)}/restore`,
      {},
    );
  }
}
