import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EXPECTED_STATUSES } from '../interceptors/error.interceptor';
import { UserApp } from '../models';
import { API_BASE_URL } from './api.config';

/**
 * Profile photos. Apart from {@link UserApi}, which is the administrator's: every signed-in
 * screen reads a photo, and a person changes their own.
 */
@Injectable({ providedIn: 'root' })
export class UserPhotoApi {
  private readonly http = inject(HttpClient);

  /**
   * The picture itself. Fetched rather than put in an `<img src>`: the token travels in a header,
   * which an image request cannot carry. `version` makes the URL change with the photo, so the
   * browser may cache it for good. A photo gone meanwhile (404) is not worth a toast.
   */
  photo(userId: number, version: number): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/users/${userId}/photo`, {
      params: { v: version },
      responseType: 'blob',
      context: new HttpContext().set(EXPECTED_STATUSES, [404]),
    });
  }

  /** Sets or replaces the photo; answers the account with its new `photoVersion`. */
  upload(userId: number, image: Blob): Observable<UserApp> {
    const body = new FormData();
    body.append('file', image, 'photo.jpg');
    return this.http.put<UserApp>(`${API_BASE_URL}/users/${userId}/photo`, body);
  }

  remove(userId: number): Observable<UserApp> {
    return this.http.delete<UserApp>(`${API_BASE_URL}/users/${userId}/photo`);
  }
}
