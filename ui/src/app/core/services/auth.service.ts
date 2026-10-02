import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, tap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { API_BASE_URL } from '../api/api.config';
import {
  DEFAULT_SHOP_SETTINGS,
  LoginRequest,
  LoginResponse,
  RoleApp,
  Session,
  ShopSettings,
  UserApp,
  authorityOf,
  businessTypeInfo,
} from '../models';
import { navigationFor, ownsSegment } from '../config/navigation';
import { TokenStorage } from './token-storage.service';

const ANONYMOUS: Session = {
  authenticated: false,
  user: null,
  roleLabel: null,
  homePath: '/login',
  authorities: [],
  settings: null,
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
  /** Every job the account may do — several when one person runs the whole shop. */
  readonly roles = computed<readonly RoleApp[]>(() => this.session().user?.roles ?? []);
  readonly roleLabel = computed(() => this.session().roleLabel ?? '');
  readonly homePath = computed(() => this.session().homePath);

  /** How the shop works. The defaults stand in while signed out, when nothing reads them. */
  readonly settings = computed<ShopSettings>(() => this.session().settings ?? DEFAULT_SHOP_SETTINGS);
  /** The words of the shop's business: "Cuisine" and "Table" in a restaurant. */
  readonly words = computed(() => businessTypeInfo(this.settings().businessType).vocabulary);

  /** Sidebar of the current account: one section per role, empty while signed out. */
  readonly menu = computed(() => navigationFor(this.roles(), this.settings()));


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

  /** A configuration just saved: the menu and the screens follow it without signing in again. */
  applySettings(settings: ShopSettings): void {
    this.session.update((session) => ({ ...session, settings }));
  }

  /**
   * The signed-in account changed — a new photo, a corrected name: every screen showing the
   * person follows without signing in again. Ignored for any other account.
   */
  applyCurrentUser(user: UserApp): void {
    this.session.update((session) =>
      session.user?.id === user.id ? { ...session, user: { ...session.user, ...user } } : session,
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

  /** True when any of the account's roles owns that section of the router. */
  owns(segment: string): boolean {
    return ownsSegment(this.roles(), segment);
  }

  goHome(): void {
    void this.router.navigateByUrl(this.homePath());
  }
}
