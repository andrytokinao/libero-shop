import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { CashRemittance } from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RemittanceStatusBadgeComponent } from '../../shared/components/status-badges.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-remittance-inbox',
  standalone: true,
  imports: [DatePipe, KpiCardComponent, RemittanceStatusBadgeComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card
        label="En attente de confirmation"
        [value]="pendingTotal() | ariary"
        [hint]="pending().length + ' bordereau(x)'"
      />
      <app-kpi-card label="Confirmé aujourd'hui" [value]="confirmedTotal() | ariary" />
      <app-kpi-card
        label="Encore en main des agents"
        [value]="cashInHandTotal() | ariary"
        hint="pas encore apporté à la caisse"
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
                <td class="num">{{ remittance.amount | ariary }}</td>
                <td>
                  <button class="btn small" type="button" (click)="confirm(remittance)">
                    Confirmer réception
                  </button>
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
  private readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly pending = this.store.pendingRemittances;
  protected readonly confirmed = this.store.confirmedRemittances;

  protected readonly pendingTotal = computed(() => ShopStore.total(this.pending()));
  protected readonly confirmedTotal = computed(() => ShopStore.total(this.confirmed()));
  protected readonly cashInHandTotal = computed(() => ShopStore.total(this.store.depotCashInHand()));

  protected confirm(remittance: CashRemittance): void {
    this.store.confirmRemittance(remittance.id, this.session.currentUser());
    this.toasts.show(
      `Versement V-${remittance.id} confirmé reçu à la caisse ` +
        `(${remittance.amount.toLocaleString('fr-FR')} Ar).`,
    );
  }
}
