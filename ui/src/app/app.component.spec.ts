import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NEVER } from 'rxjs';
import { RealtimeService } from './core/realtime/realtime.service';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { API_BASE_URL } from './core/api/api.config';
import { LicenseState, LicenseStatus, RoleApp, Session } from './core/models';
import { AuthService } from './core/services/auth.service';
import { TokenStorage } from './core/services/token-storage.service';

const CASHIER_SESSION: Session = {
  authenticated: true,
  user: {
    id: 1,
    fullName: 'Fatima Randria',
    username: 'fatima',
    roles: [RoleApp.CASHIER],
    enabled: true,
  },
  roleLabel: 'Responsable de caisse',
  homePath: '/caisse/tableau-de-bord',
  authorities: ['ROLE_CASHIER'],
};

/** The small grocery: one person sells at the desk and hands the goods over at the depot. */
const DOUBLE_ROLE_SESSION: Session = {
  authenticated: true,
  user: {
    id: 6,
    fullName: 'Soa Ravelo',
    username: 'soa',
    roles: [RoleApp.CASHIER, RoleApp.DEPOT_AGENT],
    enabled: true,
  },
  roleLabel: 'Caisse · Dépôt',
  homePath: '/caisse/tableau-de-bord',
  authorities: ['ROLE_CASHIER', 'ROLE_DEPOT_AGENT'],
};

/** A licence with room to spare, so the shell renders without its warning bar. */
const HEALTHY_LICENSE: LicenseStatus = {
  licensed: true,
  state: LicenseState.ACTIVE,
  writesAllowed: true,
  customerName: 'Supermarche Houssen Analakely',
  customerId: 'CUST-0042',
  plan: null,
  expiresOn: '2027-09-24',
  daysUntilExpiry: 320,
  daysUntilReadOnly: 335,
  message: 'Licence active.',
  machineFingerprint: 'LS1-4KQ8T-9WZ2M-H7PXR-C3NVB',
};

describe('AppComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        // The shell's bell listens for pushed notifications; a test of the shell must not
        // open a real socket to the test runner's server.
        {
          provide: RealtimeService,
          useValue: { notifications$: NEVER, state: signal('offline') },
        },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('shows only the login outlet while signed out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.side')).toBeNull();
  });

  it('renders the sidebar with the role menu once the session resolves', () => {
    // Without a stored token there is nothing to resolve and no call is made, so the
    // signed-in case has to start from a token the way a returning browser would.
    TestBed.inject(TokenStorage).save('jeton-de-test', 3600);

    const auth = TestBed.inject(AuthService);
    auth.ensureLoaded().subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/session`).flush(CASHIER_SESSION);

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    // The shell carries the licence bar, which asks for the status as soon as it renders.
    httpMock.expectOne(`${API_BASE_URL}/license/status`).flush(HEALTHY_LICENSE);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.lic-banner')).toBeNull();

    const sidebar = fixture.nativeElement.querySelector('.side') as HTMLElement;
    expect(sidebar.textContent).toContain('Responsable de caisse');
    expect(sidebar.querySelectorAll('nav.menu a').length).toBe(5);
    // One role, one section: no heading to tell apart, so the menu stays flat.
    expect(sidebar.querySelectorAll('nav.menu .group').length).toBe(0);
  });

  it('gives an account that holds two roles both menus, under their headings', () => {
    TestBed.inject(TokenStorage).save('jeton-de-test', 3600);

    const auth = TestBed.inject(AuthService);
    auth.ensureLoaded().subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/session`).flush(DOUBLE_ROLE_SESSION);

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${API_BASE_URL}/license/status`).flush(HEALTHY_LICENSE);
    fixture.detectChanges();

    const sidebar = fixture.nativeElement.querySelector('.side') as HTMLElement;
    // Five entries at the desk, four at the depot, and a heading over each set.
    expect(sidebar.querySelectorAll('nav.menu a').length).toBe(9);
    const groups = Array.from(sidebar.querySelectorAll('nav.menu .group')).map((el) =>
      el.textContent?.trim(),
    );
    expect(groups).toEqual(['Caisse', 'Dépôt']);
    // Both sections are reachable; the guard no longer owns a single segment.
    expect(auth.owns('caisse')).toBeTrue();
    expect(auth.owns('depot')).toBeTrue();
    expect(auth.owns('admin')).toBeFalse();
  });

  it('opens and closes the mobile menu from the top bar', () => {
    TestBed.inject(TokenStorage).save('jeton-de-test', 3600);

    const auth = TestBed.inject(AuthService);
    auth.ensureLoaded().subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/session`).flush(CASHIER_SESSION);

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${API_BASE_URL}/license/status`).flush(HEALTHY_LICENSE);
    fixture.detectChanges();

    const shell = fixture.nativeElement.querySelector('.app') as HTMLElement;
    const burger = fixture.nativeElement.querySelector('.burger') as HTMLButtonElement;
    expect(shell.classList).not.toContain('nav-open');

    burger.click();
    fixture.detectChanges();
    expect(shell.classList).toContain('nav-open');

    // Tapping beside the drawer puts the page back in front.
    (fixture.nativeElement.querySelector('.scrim') as HTMLElement).click();
    fixture.detectChanges();
    expect(shell.classList).not.toContain('nav-open');
  });

  afterEach(() => {
    httpMock.verify();
    // localStorage outlives the TestBed; a token left behind would decide the next test.
    TestBed.inject(TokenStorage).clear();
  });
});
