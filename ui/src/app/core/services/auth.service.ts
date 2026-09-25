import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, tap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { API_BASE_URL } from '../api/api.config';
import { LoginRequest, RoleApp, Session, UserApp, authorityOf } from '../models';
import { ROLE_NAVIGATION } from '../config/navigation';

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
 * to the user, not a security control.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

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
    return this.http.get<Session>(`${API_BASE_URL}/auth/session`).pipe(
      catchError(() => of(ANONYMOUS)),
      tap((session) => {
        this.session.set(session);
        this.resolved.set(true);
      }),
    );
  }

  login(credentials: LoginRequest): Observable<Session> {
    return this.http.post<Session>(`${API_BASE_URL}/auth/login`, credentials).pipe(
      tap((session) => {
        this.session.set(session);
        this.resolved.set(true);
      }),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}).pipe(
      catchError(() => of(void 0)),
      tap(() => this.clear()),
      map(() => void 0),
    );
  }

  /** Drops the local session without calling the server — used when a 401 comes back. */
  clear(): void {
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
