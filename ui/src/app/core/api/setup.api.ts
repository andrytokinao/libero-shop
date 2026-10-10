import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { SILENT_ERRORS } from '../interceptors/error.interceptor';
import { UserApp } from '../models';
import { API_BASE_URL } from './api.config';

/** Mirrors SetupController.SetupStatus. */
export interface SetupStatus {
  /** No account exists yet: nobody can sign in. */
  required: boolean;
  /** The first account can be created from this device — only the server's own computer. */
  allowedHere: boolean;
}

/** The first account, as its owner fills it in — mirrors CreateFirstAdminRequest. */
export interface FirstAdminRequest {
  fullName: string;
  username: string;
  password: string;
}

const DONE: SetupStatus = { required: false, allowedHere: false };

/**
 * The first start of an installation: is there an account yet, and the call that creates the
 * first one.
 *
 * <p>An installation only ever goes from "nobody" to "somebody", so the answer "not required" is
 * kept for good and the question is not asked again; "required" is asked again each time, since
 * someone at the server may have created the account meanwhile.
 */
@Injectable({ providedIn: 'root' })
export class SetupApi {
  private readonly http = inject(HttpClient);
  private readonly done = signal(false);

  /**
   * A server that cannot be reached, or one from before this endpoint, answers "not required":
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
        tap((status) => this.done.set(!status.required)),
      );
  }

  /** Refused by the server once any account exists, and from any machine but its own. */
  createFirstAdmin(request: FirstAdminRequest): Observable<UserApp> {
    return this.http
      .post<UserApp>(`${API_BASE_URL}/setup/admin`, request, {
        context: new HttpContext().set(SILENT_ERRORS, true),
      })
      .pipe(tap(() => this.done.set(true)));
  }
}
