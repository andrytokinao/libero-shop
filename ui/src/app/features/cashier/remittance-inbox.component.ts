import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { RemittanceApi } from '../../core/api/remittance.api';
import { CashRemittance, RemittanceStatus, RoleApp } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { ToastService } from '../../core/services/toast.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RemittanceStatusBadgeComponent } from '../../shared/components/status-badges.component';
import { HasRoleDirective } from '../../shared/directives/has-role.directive';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-remittance-inbox',
  standalone: true,
  imports: [
    DatePipe,
    KpiCardComponent,
    RemittanceStatusBadgeComponent,
    HasRoleDirective,
    AriaryPipe,
  ],
  styles: `
    .small {
      font-size: 12px;
    }

    .check {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: 12px;
    }
  `,
  template: `
    <div class="grid g2">
      <app-kpi-card
        label="En attente de confirmation"
        [value]="pendingTotal() | ariary"
        [hint]="pending().length + ' bordereau(x)'"
      />
      <app-kpi-card
        label="Total confirmé"
        [value]="confirmedTotal() | ariary"
        [hint]="confirmed().length + ' bordereau(x)'"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Versements en attente de confirmation
        <small>argent apporté par les agents de dépôt</small>
      </h2>
      @if (pending().length) {
        <table>
          <thead>
            <tr>
              <th>N° versement</th>
              <th>Agent</th>
              <th>Date</th>
              <th>Commandes</th>
              <th class="num">Montant</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (remittance of pending(); track remittance.id) {
              <tr>
                <td>V-{{ remittance.id }}</td>
                <td>{{ remittance.submittedBy.fullName }}</td>
                <td class="muted">{{ remittance.remittanceDate | date: 'dd/MM HH:mm' }}</td>
                <td>
                  <!-- What to count against: each order and its amount. -->
                  @for (invoice of remittance.invoices; track invoice.invoiceId) {
                    <div class="small">
                      {{ invoice.invoiceNumber }} · {{ invoice.clientName }} ·
                      {{ invoice.amount | ariary }}
                    </div>
                  }
                </td>
                <td class="num"><strong>{{ remittance.amount | ariary }}</strong></td>
                <td>
                  <!-- Only a cash desk may acknowledge a slip; the API enforces it too.
                       Two steps: signing for money not counted is the mistake to prevent. -->
                  <ng-container *appHasRole="RoleApp.CASHIER">
                    @if (checking() === remittance.id) {
                      <div class="check">
                        <span>Avez-vous compté {{ remittance.amount | ariary }} ?</span>
                        <button
                          class="btn small"
                          type="button"
                          [disabled]="busy()"
                          (click)="confirm(remittance)"
                        >
                          Oui, argent reçu
                        </button>
                        <button class="btn small ghost" type="button" (click)="checking.set(null)">
                          Annuler
                        </button>
                      </div>
                    } @else {
                      <button
                        class="btn small"
                        type="button"
                        [disabled]="busy()"
                        (click)="checking.set(remittance.id)"
                      >
                        Confirmer réception
                      </button>
                    }
                  </ng-container>
                </td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun versement en attente.</div>
      }
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Versements confirmés</h2>
      @if (confirmed().length) {
        <table>
          <thead>
            <tr>
              <th>N° versement</th>
              <th>Agent</th>
              <th>Date</th>
              <th class="num">Montant</th>
              <th>Statut</th>
            </tr>
          </thead>
          <tbody>
            @for (remittance of confirmed(); track remittance.id) {
              <tr>
                <td>V-{{ remittance.id }}</td>
                <td>{{ remittance.submittedBy.fullName }}</td>
                <td class="muted">{{ remittance.remittanceDate | date: 'dd/MM HH:mm' }}</td>
                <td class="num">{{ remittance.amount | ariary }}</td>
                <td>
                  <app-remittance-status-badge
                    [status]="remittance.status"
                    [confirmedBy]="remittance.confirmedBy"
                  />
                </td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun versement confirmé pour le moment.</div>
      }
    </div>
  `,
})
export class RemittanceInboxComponent {
  private readonly api = inject(RemittanceApi);
  private readonly toasts = inject(ToastService);

  protected readonly RoleApp = RoleApp;
  protected readonly busy = signal(false);
  /** The slip whose "have you counted it?" question is open. */
  protected readonly checking = signal<number | null>(null);

  private readonly resource = apiResource<CashRemittance[]>([], () => this.api.search());
  private readonly remittances = this.resource.value;

  constructor() {
    // A slip brought by the depot appears without a refresh; one confirmed at another till
    // leaves the list.
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  protected readonly pending = computed(() =>
    this.remittances().filter((r) => r.status === RemittanceStatus.PENDING),
  );
  protected readonly confirmed = computed(() =>
    this.remittances().filter((r) => r.status === RemittanceStatus.CONFIRMED),
  );

  protected readonly pendingTotal = computed(() =>
    this.pending().reduce((total, r) => total + r.amount, 0),
  );
  protected readonly confirmedTotal = computed(() =>
    this.confirmed().reduce((total, r) => total + r.amount, 0),
  );

  protected confirm(remittance: CashRemittance): void {
    this.busy.set(true);
    this.api.confirm(remittance.id).subscribe({
      next: (confirmed) => {
        this.busy.set(false);
        this.checking.set(null);
        this.resource.reload();
        this.toasts.show(
          `Versement V-${confirmed.id} confirmé reçu à la caisse ` +
            `(${confirmed.amount.toLocaleString('fr-FR')} Ar) : commande(s) payée(s).`,
        );
      },
      error: () => {
        this.busy.set(false);
        this.checking.set(null);
        this.resource.reload();
      },
    });
  }
}
