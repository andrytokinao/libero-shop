import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api.config';
import { LicenseState, LicenseStatus } from '../../core/models';
import { LicenseBannerComponent } from './license-banner.component';

function statusOf(overrides: Partial<LicenseStatus>): LicenseStatus {
  return {
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
    ...overrides,
  };
}

describe('LicenseBannerComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<LicenseBannerComponent>;

  /** Renders the bar against a given status, the way the shell would. */
  function render(status: LicenseStatus): HTMLElement {
    fixture = TestBed.createComponent(LicenseBannerComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${API_BASE_URL}/license/status`).flush(status);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LicenseBannerComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('stays out of the way while the licence has room to spare', () => {
    expect(render(statusOf({})).querySelector('.lic-banner')).toBeNull();
  });

  it('warns in amber as the expiry date comes into range', () => {
    const banner = render(statusOf({ daysUntilExpiry: 12, daysUntilReadOnly: 27 }));

    const bar = banner.querySelector('.lic-banner') as HTMLElement;
    expect(bar).not.toBeNull();
    expect(bar.classList).not.toContain('danger');
    expect(bar.textContent).toContain('12 jour(s)');
    // Still recording sales, so the cashier may put the bar away for now.
    expect(bar.querySelector('.x')).not.toBeNull();
  });

  it('cannot be dismissed once writes are refused', () => {
    const banner = render(
      statusOf({
        state: LicenseState.READ_ONLY,
        writesAllowed: false,
        daysUntilExpiry: -20,
        daysUntilReadOnly: -5,
      }),
    );

    const bar = banner.querySelector('.lic-banner') as HTMLElement;
    expect(bar.classList).toContain('danger');
    // The bar is the explanation for every sale the screen is about to refuse; there is
    // no close button to make it go away.
    expect(bar.querySelector('.x')).toBeNull();
  });

  it('treats the grace window as urgent, not as a hint', () => {
    const banner = render(
      statusOf({ state: LicenseState.GRACE, daysUntilExpiry: -3, daysUntilReadOnly: 12 }),
    );

    const bar = banner.querySelector('.lic-banner') as HTMLElement;
    expect(bar.classList).toContain('danger');
    expect(bar.textContent).toContain('12 jour(s)');
  });

  it('fetches the renewal code when the dialog is opened', () => {
    const banner = render(statusOf({ state: LicenseState.TRIAL, licensed: false }));

    (banner.querySelector('.lic-banner .lnk') as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock
      .expectOne(`${API_BASE_URL}/license/renewal-code`)
      .flush({ renewalCode: 'LSR1-04JEX-397KW', instructions: ['Envoyez ce code.'] });
    fixture.detectChanges();

    const dialog = banner.querySelector('.modal') as HTMLElement;
    expect(dialog.textContent).toContain('LSR1-04JEX-397KW');
    expect(dialog.textContent).toContain('LS1-4KQ8T-9WZ2M-H7PXR-C3NVB');
  });
});
