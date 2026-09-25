import { Injectable, NgZone, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { LicenseApi } from '../api/license.api';
import {
  LICENSE_STATE_LABELS,
  LicenseSeverity,
  LicenseState,
  LicenseStatus,
  RENEWAL_WARNING_DAYS,
  RenewalCodeResponse,
} from '../models';

/**
 * How often the status is asked for again.
 *
 * <p>A cash desk is opened in the morning and left running until closing. Without this,
 * a license that lapses at midnight would still read "expire demain" on the screen the
 * staff look at the next afternoon — and the first refused sale would come as a surprise.
 */
const STATUS_REFRESH_MS = 30 * 60 * 1000;

/**
 * Holds the license status for the whole application, and decides how loudly to say it.
 *
 * <p>One copy of the status, shared by the banner in the shell and by the administration
 * screen, because both have to agree: a banner still promising thirty days while the page
 * says read-only is worse than either alone.
 *
 * <p>Nothing here re-computes a date. The server sends `daysUntilExpiry`,
 * `daysUntilReadOnly` and a French message already worked out against the clock its own
 * `ClockGuard` corrected — the browser's clock is not a party to the licence.
 */
@Injectable({ providedIn: 'root' })
export class LicenseService {
  private readonly api = inject(LicenseApi);
  private readonly zone = inject(NgZone);

  private readonly state = signal<LicenseStatus | null>(null);
  private readonly code = signal<RenewalCodeResponse | null>(null);
  private readonly inFlight = signal(false);
  private readonly dismissedBanner = signal(false);
  private readonly dialogOpen = signal(false);

  /** Null until the first answer comes back; the UI says nothing in the meantime. */
  readonly status = this.state.asReadonly();
  readonly renewalCode = this.code.asReadonly();
  readonly busy = this.inFlight.asReadonly();
  readonly dialogVisible = this.dialogOpen.asReadonly();

  constructor() {
    this.refresh();
    // Outside the Angular zone so a repeating timer never holds a test waiting for
    // stability; the refresh itself is run back inside it so signals update the view.
    this.zone.runOutsideAngular(() => {
      setInterval(() => this.zone.run(() => this.refresh()), STATUS_REFRESH_MS);
    });
  }

  /** Human label of the current state, e.g. "Expirée — période de tolérance". */
  readonly stateLabel = computed(() => {
    const status = this.state();
    return status ? LICENSE_STATE_LABELS[status.state] : '';
  });

  /**
   * How urgent the situation is.
   *
   * <p>`GRACE` counts as danger rather than warning on purpose: the licence has already
   * expired, and what is running is a countdown to a cash desk that can no longer record
   * a sale. Calling that "attention" would be understating it.
   */
  readonly severity = computed<LicenseSeverity>(() => {
    const status = this.state();
    if (!status) {
      return 'ok';
    }
    if (!status.writesAllowed || status.state === LicenseState.GRACE) {
      return 'danger';
    }
    if (status.state === LicenseState.TRIAL) {
      return 'warn';
    }
    if (status.expiresOn !== null && status.daysUntilExpiry <= RENEWAL_WARNING_DAYS) {
      return 'warn';
    }
    return 'ok';
  });

  /**
   * Whether the shell shows its bar.
   *
   * <p>A blocked installation cannot dismiss it: that bar is the explanation for every
   * sale the screen is about to refuse.
   */
  readonly bannerVisible = computed(() => {
    const status = this.state();
    if (!status || this.severity() === 'ok') {
      return false;
    }
    return !status.writesAllowed || !this.dismissedBanner();
  });

  /** One short line for the bar. The server's own `message` is kept for the dialog. */
  readonly headline = computed(() => {
    const status = this.state();
    if (!status) {
      return '';
    }
    switch (status.state) {
      case LicenseState.ACTIVE:
        return `Votre licence expire dans ${status.daysUntilExpiry} jour(s).`;
      case LicenseState.GRACE:
        return (
          `Licence expirée. L'enregistrement des ventes sera bloqué dans ` +
          `${status.daysUntilReadOnly} jour(s).`
        );
      case LicenseState.READ_ONLY:
        return "Licence expirée : aucune nouvelle opération ne peut être enregistrée.";
      case LicenseState.TRIAL:
        return (
          `Période d'essai — ${status.daysUntilExpiry} jour(s) restant(s). ` +
          `Aucune licence n'est installée sur ce poste.`
        );
      case LicenseState.TRIAL_EXPIRED:
        return "Période d'essai terminée : aucune nouvelle opération ne peut être enregistrée.";
      case LicenseState.UNLICENSED:
        return "Aucune licence valide n'est installée sur ce poste.";
    }
  });

  refresh(): void {
    this.inFlight.set(true);
    this.api.status().subscribe({
      next: (status) => {
        this.state.set(status);
        this.inFlight.set(false);
      },
      // A failure leaves the previous status in place: the error interceptor has already
      // said something, and blanking the banner on a hiccup would hide a real warning.
      error: () => this.inFlight.set(false),
    });
  }

  /** Fetched on demand — the code is only needed once someone is about to order. */
  loadRenewalCode(): void {
    this.api.renewalCode().subscribe((code) => this.code.set(code));
  }

  /**
   * Installs a `.lic` and adopts the status it answers with.
   *
   * <p>A rejected file never reaches here: the server refuses a bad signature, a foreign
   * machine, or a licence that does not expire later than the one already installed.
   */
  install(licenseFileContent: string): Observable<LicenseStatus> {
    this.inFlight.set(true);
    return this.api.install(licenseFileContent).pipe(
      tap({
        next: (status) => this.adopt(status),
        error: () => this.inFlight.set(false),
      }),
    );
  }

  /** Triggers an immediate online renewal attempt. */
  renew(): Observable<LicenseStatus> {
    this.inFlight.set(true);
    return this.api.renew().pipe(
      tap({
        next: (status) => this.adopt(status),
        error: () => this.inFlight.set(false),
      }),
    );
  }

  openDialog(): void {
    this.dialogOpen.set(true);
    if (this.code() === null) {
      this.loadRenewalCode();
    }
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
  }

  /** Hides the bar until the next refresh, or until the installation actually blocks. */
  dismissBanner(): void {
    this.dismissedBanner.set(true);
  }

  private adopt(status: LicenseStatus): void {
    this.state.set(status);
    this.inFlight.set(false);
    this.dismissedBanner.set(false);
    // The old code carries the old expiry date; asking again is cheaper than reasoning
    // about whether it is still the one to send.
    this.code.set(null);
  }
}
