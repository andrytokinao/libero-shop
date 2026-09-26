import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LICENSE_PLAN_LABELS, LicenseState, RoleApp } from '../../core/models';
import { LicenseService } from '../../core/services/license.service';
import { ToastService } from '../../core/services/toast.service';
import { copyToClipboard } from '../../core/utils/clipboard.util';
import { HasRoleDirective } from '../directives/has-role.directive';

/**
 * The licence bar in the shell, and the dialog it opens.
 *
 * <p>Two audiences, one component. The bar is for the cashier who needs to know that the
 * sale they are about to record may be refused, so it appears for every role and says one
 * sentence. The dialog is for whoever is going to do something about it: it shows the
 * dates, the machine fingerprint and the renewal code — everything the publisher asks for
 * — without sending anyone to an administration screen their role cannot open.
 *
 * <p>Deliberately not dismissible once writes are refused: at that point the bar is the
 * explanation for every error the screen is about to produce.
 */
@Component({
  selector: 'app-license-banner',
  standalone: true,
  imports: [DatePipe, RouterLink, HasRoleDirective],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    @if (license.bannerVisible()) {
      <div class="lic-banner" [class.danger]="license.severity() === 'danger'" role="status">
        <span class="ic" aria-hidden="true">{{ license.severity() === 'danger' ? '!' : 'i' }}</span>
        <span class="msg">{{ license.headline() }}</span>
        <button class="lnk" type="button" (click)="license.openDialog()">Voir le détail</button>
        @if (license.status()?.writesAllowed) {
          <button class="x" type="button" aria-label="Masquer" (click)="license.dismissBanner()">
            ✕
          </button>
        }
      </div>
    }

    @if (license.dialogVisible()) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="lic-title">
          @if (license.status(); as status) {
            <div class="modal-head">
              <div>
                <h2 id="lic-title">Licence Libero Shop</h2>
                <span class="badge" [class]="badgeClass()">{{ license.stateLabel() }}</span>
              </div>
              <button class="x" type="button" aria-label="Fermer" (click)="license.closeDialog()">
                ✕
              </button>
            </div>

            <div class="modal-body">
              <p class="lic-message">{{ status.message }}</p>

              <!-- Where the installation sits on the ACTIVE -> GRACE -> READ_ONLY line. -->
              <div class="lic-track" aria-hidden="true">
                <span [class.on]="status.state === LicenseState.ACTIVE" class="s-ok"></span>
                <span [class.on]="status.state === LicenseState.GRACE" class="s-warn"></span>
                <span [class.on]="!status.writesAllowed" class="s-bad"></span>
              </div>
              <div class="lic-track-legend">
                <span>Active</span><span>Tolérance</span><span>Lecture seule</span>
              </div>

              <dl class="dl">
                @if (status.customerName) {
                  <dt>Client</dt>
                  <dd>{{ status.customerName }}</dd>
                }
                @if (status.plan) {
                  <dt>Formule</dt>
                  <dd>{{ planLabel() }}</dd>
                }
                @if (status.expiresOn) {
                  <dt>{{ status.licensed ? 'Expire le' : "Fin d'essai" }}</dt>
                  <dd>
                    {{ status.expiresOn | date: 'dd MMMM y' }}
                    <span class="muted">({{ remainingLabel() }})</span>
                  </dd>
                }
                <dt>Écritures</dt>
                <dd>
                  @if (status.writesAllowed) {
                    <span class="badge green">Autorisées</span>
                  } @else {
                    <span class="badge red">Refusées — lecture seule</span>
                  }
                </dd>
                <dt>Empreinte du poste</dt>
                <dd class="mono">{{ status.machineFingerprint }}</dd>
              </dl>

              <div class="lic-code">
                <div class="lbl">Code de renouvellement</div>
                @if (license.renewalCode(); as code) {
                  <div class="code-box">{{ code.renewalCode }}</div>
                  <button class="btn small ghost" type="button" (click)="copy(code.renewalCode)">
                    Copier le code
                  </button>
                  <ul class="hints">
                    @for (line of code.instructions; track line) {
                      <li>{{ line }}</li>
                    }
                  </ul>
                } @else {
                  <div class="muted">Chargement du code…</div>
                }
              </div>
            </div>

            <div class="modal-foot">
              <!-- Installing a licence is an administrative act; the API refuses it to
                   anyone else, so the link is only offered where it would work. -->
              <a
                *appHasRole="RoleApp.SUPER_ADMIN"
                class="btn"
                [routerLink]="'/admin/licence'"
                (click)="license.closeDialog()"
              >
                Gérer la licence
              </a>
              <button class="btn ghost" type="button" (click)="license.closeDialog()">Fermer</button>
            </div>
          }
        </div>
      </div>
    }
  `,
})
export class LicenseBannerComponent {
  protected readonly license = inject(LicenseService);
  private readonly toasts = inject(ToastService);

  protected readonly RoleApp = RoleApp;
  protected readonly LicenseState = LicenseState;

  protected badgeClass(): string {
    switch (this.license.severity()) {
      case 'danger':
        return 'red';
      case 'warn':
        return 'amber';
      default:
        return 'green';
    }
  }

  protected planLabel(): string {
    const plan = this.license.status()?.plan;
    return plan ? LICENSE_PLAN_LABELS[plan] : '';
  }

  /** "dans 21 jour(s)" or "il y a 3 jour(s)" — the sign of the count carries the tense. */
  protected remainingLabel(): string {
    const days = this.license.status()?.daysUntilExpiry ?? 0;
    return days >= 0 ? `dans ${days} jour(s)` : `il y a ${-days} jour(s)`;
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.license.closeDialog();
    }
  }

  protected onEscape(): void {
    if (this.license.dialogVisible()) {
      this.license.closeDialog();
    }
  }

  protected copy(value: string): void {
    void copyToClipboard(value).then((copied) =>
      this.toasts.show(
        copied
          ? 'Code de renouvellement copié. Envoyez-le à votre revendeur.'
          : 'Copie impossible sur cette connexion : sélectionnez le code à la main.',
      ),
    );
  }
}
