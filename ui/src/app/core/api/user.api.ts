import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  CreateUserRequest,
  SetPasswordRequest,
  UpdateUserRequest,
  UserActivity,
  UserApp,
} from '../models';

/**
 * The accounts, and the four things a super-admin does to them.
 *
 * <p>No `delete`, because the server has no such endpoint: a departure is a disabled
 * account, so the sales and hand-overs it signed keep naming someone.
 *
 * <p>Every write answers with the account as it now stands, so a screen can put the row back
 * without re-fetching the list — though the users screen reloads anyway, since the activity
 * figures beside each row come from the same call.
 */
@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  users(): Observable<UserApp[]> {
    return this.http.get<UserApp[]>(`${API_BASE_URL}/users`);
  }

  /** The accounts with what each of them did today, which is what the admin screen lists. */
  activity(): Observable<UserActivity[]> {
    return this.http.get<UserActivity[]>(`${API_BASE_URL}/users/activity`);
  }

  create(request: CreateUserRequest): Observable<UserApp> {
    return this.http.post<UserApp>(`${API_BASE_URL}/users`, request);
  }

  /** Name and roles. The handle is not editable — see UpdateUserRequest. */
  update(id: number, request: UpdateUserRequest): Observable<UserApp> {
    return this.http.put<UserApp>(`${API_BASE_URL}/users/${id}`, request);
  }

  setEnabled(id: number, enabled: boolean): Observable<UserApp> {
    const action = enabled ? 'enable' : 'disable';
    return this.http.post<UserApp>(`${API_BASE_URL}/users/${id}/${action}`, {});
  }

  setPassword(id: number, request: SetPasswordRequest): Observable<UserApp> {
    return this.http.put<UserApp>(`${API_BASE_URL}/users/${id}/password`, request);
  }
}
