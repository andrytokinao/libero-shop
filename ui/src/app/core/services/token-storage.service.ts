import { Injectable, signal } from '@angular/core';

/** Namespaced, so another app served from the same origin cannot clobber them. */
const TOKEN_KEY = 'libertyshop.access-token';
const EXPIRY_KEY = 'libertyshop.access-token-expiry';

/**
 * Keeps the access token across page loads.
 *
 * <p>Separate from `AuthService` on purpose: the interceptor needs the token on every
 * request, and injecting a service that itself depends on `HttpClient` into an
 * `HttpClient` interceptor is how circular dependencies happen. Nothing here calls the
 * server.
 *
 * <p>`localStorage` is a deliberate trade. A token kept there survives a refresh and a
 * reopened tab, which is what a cashier expects from a till application, but any script
 * running on this origin can read it — the session cookie it replaces could not be read.
 * The defence is therefore upstream: no `innerHTML` with server data, and no third-party
 * script on this origin.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private readonly current = signal<string | null>(restore());

  /** The token to send, or null when signed out. */
  readonly token = this.current.asReadonly();

  /** @param expiresIn seconds of validity, as returned by POST /api/auth/login */
  save(accessToken: string, expiresIn: number): void {
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(EXPIRY_KEY, String(Date.now() + expiresIn * 1000));
    this.current.set(accessToken);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRY_KEY);
    this.current.set(null);
  }
}

/**
 * Reads the stored token at start-up, dropping it if its time is up.
 *
 * <p>The expiry is only an optimisation — it saves a round trip whose answer would be a
 * 401 — and it is not trusted: the server checks the `exp` claim it signed itself, which
 * no one can edit from here.
 */
function restore(): string | null {
  const token = localStorage.getItem(TOKEN_KEY);
  const expiry = Number(localStorage.getItem(EXPIRY_KEY));
  if (!token || !Number.isFinite(expiry) || expiry <= Date.now()) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRY_KEY);
    return null;
  }
  return token;
}
