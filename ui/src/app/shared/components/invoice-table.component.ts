import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Invoice } from '../../core/models';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { DeliveryStatusBadgeComponent, PaymentStatusBadgeComponent } from './status-badges.component';

/** The invoice list shared by the cash-desk, depot and admin screens. */
@Component({
  selector: 'app-invoice-table',
  standalone: true,
  imports: [DatePipe, AriaryPipe, PaymentStatusBadgeComponent, DeliveryStatusBadgeComponent],
  template: `
    @if (invoices.length) {
      <table>
        <thead>
          <tr>
            <th>N° facture</th>
            @if (showDate) {
              <th>Date</th>
            }
            <th>Client</th>
            @if (showSeller) {
              <th>Vendeur</th>
            }
            <th class="num">Articles</th>
            <th class="num">Montant</th>
            <th>Paiement</th>
            <th>Remise</th>
            @if (actionLabel) {
              <th></th>
            }
          </tr>
        </thead>
        <tbody>
          @for (invoice of invoices; track invoice.id) {
            <tr>
              <td>{{ invoice.invoiceNumber }}</td>
              @if (showDate) {
                <td class="muted">{{ invoice.invoiceDate | date: 'dd/MM HH:mm' }}</td>
              }
              <td>{{ invoice.clientName }}</td>
              @if (showSeller) {
                <td>{{ invoice.sale.seller.fullName }}</td>
              }
              <td class="num">{{ invoice.itemCount }}</td>
              <td class="num">{{ invoice.sale.totalAmount | ariary }}</td>
              <td><app-payment-status-badge [status]="invoice.paymentStatus" /></td>
              <td><app-delivery-status-badge [status]="invoice.deliveryStatus" /></td>
              @if (actionLabel) {
                <td>
                  @if (actionLabel(invoice); as label) {
                    <button
                      class="btn small"
                      type="button"
                      [disabled]="busy"
                      (click)="action.emit(invoice)"
                    >
                      {{ label }}
                    </button>
                  } @else {
                    —
                  }
                </td>
              }
            </tr>
          }
        </tbody>
      </table>
    } @else {
      <div class="empty">{{ emptyMessage }}</div>
    }
  `,
})
export class InvoiceTableComponent {
  @Input({ required: true }) invoices: readonly Invoice[] = [];
  @Input() showSeller = true;
  @Input() showDate = false;
  @Input() emptyMessage = 'Aucune facture pour le moment.';
  /** Blocks the row actions while a call is in flight, so nothing is posted twice. */
  @Input() busy = false;
  /** Returns the button label for a row, or null to show no action. */
  @Input() actionLabel?: (invoice: Invoice) => string | null;
  @Output() readonly action = new EventEmitter<Invoice>();
}
