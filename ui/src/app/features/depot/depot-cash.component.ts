import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { RemittanceApi } from '../../core/api/remittance.api';
import { CashRemittance } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { RemittanceStatusBadgeComponent } from '../../shared/components/status-badges.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { CashInHandComponent } from './cash-in-hand.component';

/** The storekeeper's cash: what they hold, and what they brought to the desk. */
@Component({
  selector: 'app-depot-cash',
  standalone: true,
  imports: [DatePipe, CashInHandComponent, RemittanceStatusBadgeComponent, AriaryPipe],
  template: `
    <div class="grid g2">
      <app-cash-in-hand (remitted)="remittanceResource.reload()" />

      <div class="card">
        <h2>Mes versements <small>confirmés par le caissier une fois l'argent compté</small></h2>
        @if (myRemittances().length) {
          <table>
            <thead>
              <tr>
                <th>N° versement</th>
                <th>Commandes</th>
                <th>Date</th>
                <th class="num">Montant</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              @for (remittance of myRemittances(); track remittance.id) {
                <tr>
                  <td>V-{{ remittance.id }}</td>
                  <td class="muted">{{ invoiceNumbers(remittance) }}</td>
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
  private readonly api = inject(RemittanceApi);

  protected readonly remittanceResource = apiResource<CashRemittance[]>([], () =>
    this.api.search({ mine: true }),
  );
  protected readonly myRemittances = this.remittanceResource.value;

  constructor() {
    // "En attente" turns to "Confirmé" the moment the cashier signs for it.
    reloadOnTopic(this.remittanceResource, ORDERS_TOPIC);
  }

  protected invoiceNumbers(remittance: CashRemittance): string {
    return remittance.invoices.map((invoice) => invoice.invoiceNumber).join(', ');
  }
}
