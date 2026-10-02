import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RemittanceStore } from '../../core/store/remittance.store';
import { CashRemittance } from '../../core/models';
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
      <app-cash-in-hand />

      <div class="card">
        <h2>Mes versements <small>confirmés par le caissier une fois l'argent compté</small></h2>
        @if (myRemittances().length) {
          <table class="inv-table">
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

          <!-- Same rows, phone layout. Hidden by CSS above the breakpoint. -->
          <ul class="inv-cards">
            @for (remittance of myRemittances(); track remittance.id) {
              <li class="inv-card">
                <div class="head">
                  <strong>V-{{ remittance.id }}</strong>
                  <span class="muted">{{ remittance.remittanceDate | date: 'dd/MM HH:mm' }}</span>
                </div>
                <div class="who muted">{{ invoiceNumbers(remittance) }}</div>
                <div class="foot">
                  <span class="amount">{{ remittance.amount | ariary }}</span>
                  <app-remittance-status-badge
                    [status]="remittance.status"
                    [confirmedBy]="remittance.confirmedBy"
                  />
                </div>
              </li>
            }
          </ul>
        } @else {
          <div class="empty">Aucun versement effectué.</div>
        }
      </div>
    </div>
  `,
})
export class DepotCashComponent {
  // Kept current by the store: a slip just brought appears, and "En attente" turns to
  // "Confirmé" the moment the cashier signs for it.
  protected readonly myRemittances = inject(RemittanceStore).list(() => ({ mine: true })).value;

  protected invoiceNumbers(remittance: CashRemittance): string {
    return remittance.invoices.map((invoice) => invoice.invoiceNumber).join(', ');
  }
}
