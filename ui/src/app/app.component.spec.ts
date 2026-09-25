import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { API_BASE_URL } from './core/api/api.config';
import { RoleApp, Session } from './core/models';
import { AuthService } from './core/services/auth.service';
import { TokenStorage } from './core/services/token-storage.service';

const CASHIER_SESSION: Session = {
  authenticated: true,
  user: {
    id: 1,
    fullName: 'Fatima Randria',
    username: 'fatima',
    role: RoleApp.CASHIER,
    enabled: true,
  },
  roleLabel: 'Responsable de caisse',
  homePath: '/caisse/tableau-de-bord',
  authorities: ['ROLE_CASHIER'],
};

describe('AppComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
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

    const sidebar = fixture.nativeElement.querySelector('.side') as HTMLElement;
    expect(sidebar.textContent).toContain('Responsable de caisse');
    expect(sidebar.querySelectorAll('nav.menu a').length).toBe(5);
  });

  afterEach(() => {
    httpMock.verify();
    // localStorage outlives the TestBed; a token left behind would decide the next test.
    TestBed.inject(TokenStorage).clear();
  });
});
