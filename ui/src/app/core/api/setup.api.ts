import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { SILENT_ERRORS } from '../interceptors/error.interceptor';
import { UserApp } from '../models';
import { API_BASE_URL } from './api.config';

/** Mirrors SetupController.SetupStatus. */
export interface SetupStatus {
  /** The administrator account opened at installation still has its first password. */
  firstPassword: boolean;
  /** That account — only told to the server's own computer. */
  username?: string | null;
  /** Its password, likewise. */
  password?: string | null;
}

/** Mirrors AdminRecoveryService.Started. */
export interface RecoveryStarted {
  /** How long the code is good for. */
  minutes: number;
}

/** Mirrors RecoverAdminRequest. */
export interface RecoverAdminRequest {
  code: string;
  username: string;
  password: string;
}

const DONE: SetupStatus = { firstPassword: false };

/**
 * The first start of an installation: the server opened an administrator account by itself, and
 * says with which password for as long as it has not been replaced.
 *
 * <p>A first password is only ever replaced, never put back, so the answer "none" is kept for
 * good and the question is not asked again; "there is one" is asked again each time, since its
 * holder may have replaced it meanwhile.
 */
@Injectable({ providedIn: 'root' })
export class SetupApi {
  private readonly http = inject(HttpClient);
  private readonly done = signal(false);

  /**
   * A server that cannot be reached, or one from before this endpoint, answers "none":
   * the login page then shows as it always did and says what is wrong in its own words.
   */
  status(): Observable<SetupStatus> {
    if (this.done()) {
      return of(DONE);
    }
    return this.http
      .get<SetupStatus>(`${API_BASE_URL}/setup/status`, {
        context: new HttpContext().set(SILENT_ERRORS, true),
      })
      .pipe(
        catchError(() => of(DONE)),
        tap((status) => this.done.set(!status.firstPassword)),
      );
  }

  /**
   * A lost administrator password, first step: the server writes a code into a file of its own
   * computer. Refused from any other machine.
   */
  startRecovery(): Observable<RecoveryStarted> {
    return this.http.post<RecoveryStarted>(
      `${API_BASE_URL}/setup/recovery/code`,
      {},
      { context: new HttpContext().set(SILENT_ERRORS, true) },
    );
  }

  /** Second step: the code read in that file, and the new password of a super-admin account. */
  recoverAdmin(request: RecoverAdminRequest): Observable<UserApp> {
    return this.http.post<UserApp>(`${API_BASE_URL}/setup/recovery/password`, request, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }
}
