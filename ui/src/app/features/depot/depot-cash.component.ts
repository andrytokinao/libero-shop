import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RemittanceStatusBadgeComponent } from '../../shared/components/status-badges.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-depot-cash',
  standalone: true,
  imports: [DatePipe, KpiCardComponent, RemittanceStatusBadgeComponent, AriaryPipe],
  template: `
    <div class="grid g2">
      <div class="card">
        <h2>
          Espèces en main
          <small>encaissées lors des remises de commandes non payées</small>
        </h2>
        <app-kpi-card
          [flat]="true"
          label="Montant à verser à la caisse"
          [value]="cashInHandTotal() | ariary"
          [hint]="cashInHand().length + ' encaissement(s)'"
        />

        <div style="margin-top:14px;">
          @if (cashInHand().length) {
            <table>
              <thead>
                <tr>
                  <th>N° facture</th>
                  <th>Client</th>
                  <th>Date</th>
                  <th class="num">Montant</th>
                </tr>
              </thead>
              <tbody>
                @for (payment of cashInHand(); track payment.id) {
                  <tr>
                    <td>{{ payment.invoice.invoiceNumber }}</td>
                    <td>{{ payment.invoice.clientName }}</td>
                    <td class="muted">{{ payment.paymentDate | date: 'dd/MM HH:mm' }}</td>
                    <td class="num">{{ payment.amount | ariary }}</td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <div class="empty">Aucune espèce en main pour le moment.</div>
          }
        </div>

        <button
          class="btn block"
          type="button"
          style="margin-top:14px;"
          [disabled]="!cashInHand().length"
          (click)="submit()"
        >
          Verser {{ cashInHandTotal() | ariary }} à la caisse
        </button>
      </div>

      <div class="card">
        <h2>Mes versements</h2>
        @if (myRemittances().length) {
          <table>
            <thead>
              <tr>
                <th>N° versement</th>
                <th>Date</th>
                <th class="num">Montant</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              @for (remittance of myRemittances(); track remittance.id) {
                <tr>
                  <td>V-{{ remittance.id }}</td>
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
          <div class="empty">Aucun versement effectué.</div>
        }
      </div>
    </div>
  `,
})
export class DepotCashComponent {
  private readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly cashInHand = computed(() =>
    this.store.cashInHandOf(this.session.currentUser()),
  );

  protected readonly cashInHandTotal = computed(() => ShopStore.total(this.cashInHand()));

  protected readonly myRemittances = computed(() =>
    this.store.remittancesOf(this.session.currentUser()),
  );

  protected submit(): void {
    const remittance = this.store.submitRemittance(this.session.currentUser());
    if (remittance) {
      this.toasts.show(
        `Versement V-${remittance.id} de ${remittance.amount.toLocaleString('fr-FR')} Ar ` +
          `enregistré — en attente de confirmation par la caisse.`,
      );
    }
  }
}
