import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, tap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { API_BASE_URL } from '../api/api.config';
import { LoginRequest, LoginResponse, RoleApp, Session, UserApp, authorityOf } from '../models';
import { ROLE_NAVIGATION } from '../config/navigation';
import { TokenStorage } from './token-storage.service';

const ANONYMOUS: Session = {
  authenticated: false,
  user: null,
  roleLabel: null,
  homePath: '/login',
  authorities: [],
};

/**
 * Holds the session and answers the two questions the rest of the app asks: may this
 * route be entered, and should this control be rendered.
 *
 * <p>The answers come from the server — {@code GET /api/auth/session} returns the role,
 * its authorities and its landing route — so there is no second copy of the permission
 * table in the client. What the guards do here is cosmetic by design: the same rules are
 * enforced again on every request, and hiding a button the API would refuse is a courtesy
 * to the user, not a security control. The role read from the session payload is the
 * server's answer, never the token's claims decoded here: a client that read its own
 * token would be trusting a string it holds a copy of.
 *
 * <p>The token itself lives in {@link TokenStorage}; this service decides when it is
 * written and when it is thrown away.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokens = inject(TokenStorage);

  private readonly session = signal<Session>(ANONYMOUS);
  /** False until the first /session call answers, so guards can wait rather than guess. */
  private readonly resolved = signal(false);

  readonly state = this.session.asReadonly();
  readonly isAuthenticated = computed(() => this.session().authenticated);
  readonly currentUser = computed<UserApp | null>(() => this.session().user);
  readonly role = computed<RoleApp | null>(() => this.session().user?.role ?? null);
  readonly roleLabel = computed(() => this.session().roleLabel ?? '');
  readonly homePath = computed(() => this.session().homePath);

  /** Menu of the current role, empty while signed out. */
  readonly menu = computed(() => {
    const role = this.role();
    return role ? ROLE_NAVIGATION[role].items : [];
  });

  readonly initials = computed(() => {
    const user = this.currentUser();
    if (!user) {
      return '';
    }
    return user.fullName
      .split(' ')
      .map((part) => part[0])
      .slice(0, 2)
      .join('');
  });

  /**
   * Resolves the session once and caches it. Called by the guards, so a hard refresh on a
   * deep link restores the role before the route is evaluated instead of bouncing the
   * user to the login page they are already past.
   */
  ensureLoaded(): Observable<Session> {
    if (this.resolved()) {
      return of(this.session());
    }
    return this.refresh();
  }

  refresh(): Observable<Session> {
    // With no token there is nothing to ask about, and the server would answer the same.
    if (this.tokens.token() === null) {
      this.clear();
      return of(ANONYMOUS);
    }
    return this.http.get<Session>(`${API_BASE_URL}/auth/session`).pipe(
      // The token is expired, or signed with a key this backend no longer holds. Either
      // way it is dead weight, and keeping it would only break the next sign-in attempt.
      catchError(() => {
        this.tokens.clear();
        return of(ANONYMOUS);
      }),
      tap((session) => {
        this.session.set(session);
        this.resolved.set(true);
      }),
    );
  }

  login(credentials: LoginRequest): Observable<Session> {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, credentials).pipe(
      // Stored before the session is published: whatever reacts to the new role — a
      // guard, a resolver — must not fire a request the interceptor has no token for.
      tap((response) => this.tokens.save(response.accessToken, response.expiresIn)),
      map((response) => response.session),
      tap((session) => {
        this.session.set(session);
        this.resolved.set(true);
      }),
    );
  }

  /**
   * Signing out is dropping the token; the call to the server is a formality the backend
   * answers 204 to, and a failure changes nothing here.
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}).pipe(
      catchError(() => of(void 0)),
      tap(() => this.clear()),
      map(() => void 0),
    );
  }

  /** Drops the token and the session without calling the server — used on a 401. */
  clear(): void {
    this.tokens.clear();
    this.session.set(ANONYMOUS);
    this.resolved.set(true);
  }

  /** True when the session carries the given role. Used by guards and by *appHasRole. */
  hasRole(...roles: RoleApp[]): boolean {
    const granted = this.session().authorities;
    return roles.some((role) => granted.includes(authorityOf(role)));
  }

  owns(segment: string): boolean {
    const role = this.role();
    return role != null && ROLE_NAVIGATION[role].segment === segment;
  }

  goHome(): void {
    void this.router.navigateByUrl(this.homePath());
  }
}
