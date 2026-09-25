import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { FingerprintResponse, LicenseStatus, RenewalCodeResponse } from '../models';

/**
 * The license endpoints.
 *
 * <p>The three GETs are public on the server — that is deliberate, it is how a customer
 * reads the fingerprint to order a license on an installation where everything else is
 * blocked. `install` and `renew` are administrative acts and answer 403 to anyone but a
 * super admin, so the screen offering them is kept behind the same role.
 */
@Injectable({ providedIn: 'root' })
export class LicenseApi {
  private readonly http = inject(HttpClient);

  status(): Observable<LicenseStatus> {
    return this.http.get<LicenseStatus>(`${API_BASE_URL}/license/status`);
  }

  fingerprint(): Observable<FingerprintResponse> {
    return this.http.get<FingerprintResponse>(`${API_BASE_URL}/license/fingerprint`);
  }

  renewalCode(): Observable<RenewalCodeResponse> {
    return this.http.get<RenewalCodeResponse>(`${API_BASE_URL}/license/renewal-code`);
  }

  /**
   * Installs a `.lic` received out of band, typically by e-mail.
   *
   * <p>The body is the file verbatim. `text/plain` rather than JSON: the server takes the
   * raw bytes and checks the publisher's signature over them, so re-encoding the content
   * as a JSON string here would only add a step that could corrupt it.
   */
  install(licenseFileContent: string): Observable<LicenseStatus> {
    return this.http.post<LicenseStatus>(
      `${API_BASE_URL}/license/install`,
      licenseFileContent,
      { headers: new HttpHeaders({ 'Content-Type': 'text/plain' }) },
    );
  }

  /**
   * Forces an online renewal attempt, for the customer who has just paid and does not
   * want to wait for the next scheduled one. Answers 501 where online renewal is not
   * configured — not an error, simply nothing to do.
   */
  renew(): Observable<LicenseStatus> {
    return this.http.post<LicenseStatus>(`${API_BASE_URL}/license/renew`, null);
  }
}
