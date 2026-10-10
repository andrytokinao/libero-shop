import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../api/api.config';
import { RoleApp, Session } from '../models';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage.service';

function sessionOf(role: RoleApp, homePath: string): Session {
  return {
    authenticated: true,
    user: { id: 7, fullName: 'Fatima', username: 'fatima', roles: [role], enabled: true },
    roleLabel: role,
    homePath,
    authorities: [`ROLE_${role}`],
    settings: null,
  };
}

const ANONYMOUS: Session = {
  authenticated: false,
  user: null,
  roleLabel: null,
  homePath: '/login',
  authorities: [],
  settings: null,
};

/**
 * The server closed the live connection because the account's access changed: the session is
 * read again, and the screen follows — new menu, home of the new role, or the login page.
 */
describe('AuthService.reloadAfterAccessChange', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let router: { url: string; navigateByUrl: jasmine.Spy };
  let token: ReturnType<typeof signal<string | null>>;

  beforeEach(() => {
    token = signal<string | null>('jeton');
    router = { url: '/admin/factures', navigateByUrl: jasmine.createSpy('navigateByUrl') };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        {
          provide: TokenStorage,
          useValue: { token, clear: () => token.set(null), save: () => undefined },
        },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function answer(session: Session): void {
    http.expectOne(`${API_BASE_URL}/auth/session`).flush(session);
  }

  it('gives the account its new menu, and leaves a screen it no longer owns', () => {
    auth.refresh().subscribe();
    answer(sessionOf(RoleApp.SUPER_ADMIN, '/admin/tableau-de-bord'));
    expect(auth.owns('admin')).toBeTrue();

    auth.reloadAfterAccessChange();
    answer(sessionOf(RoleApp.CASHIER, '/caisse/vente'));

    expect(auth.roles()).toEqual([RoleApp.CASHIER]);
    expect(auth.owns('admin')).toBeFalse();
    expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/caisse/vente');
  });

  it('stays where it is when the screen is still the account’s', () => {
    router.url = '/caisse/vente?table=4';

    auth.reloadAfterAccessChange();
    answer(sessionOf(RoleApp.CASHIER, '/caisse/vente'));

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('sends an account that can no longer sign in to the login page', () => {
    auth.reloadAfterAccessChange();
    http
      .expectOne(`${API_BASE_URL}/auth/session`)
      .flush({ message: 'Compte desactive' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBeFalse();
    expect(token()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/login');
  });

  it('sends a session closed by the server, answered as anonymous, to the login page too', () => {
    auth.reloadAfterAccessChange();
    answer(ANONYMOUS);

    expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/login');
  });
});
