import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { Invoice } from '../../core/models';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { InvoiceDetailDialogComponent } from './invoice-detail-dialog.component';
import { DeliveryStatusBadgeComponent, PaymentStatusBadgeComponent } from './status-badges.component';

/**
 * The invoice list shared by the cash-desk, depot and admin screens.
 *
 * <p>Two renderings of the same rows: a table on a desktop, a stack of cards on a phone
 * — an invoice has nine columns, which no phone shows without sideways scrolling. Either
 * one opens the articles of an invoice in a dialog, because the depot checks the goods
 * against that list before handing an order over.
 */
@Component({
  selector: 'app-invoice-table',
  standalone: true,
  imports: [
    DatePipe,
    AriaryPipe,
    PaymentStatusBadgeComponent,
    DeliveryStatusBadgeComponent,
    InvoiceDetailDialogComponent,
  ],
  template: `
    @if (invoices.length) {
      <table class="inv-table">
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
            @if (showDetail) {
              <th class="col-detail"></th>
            }
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
              @if (showDetail) {
                <td class="col-detail">
                  <button
                    class="lnk-detail"
                    type="button"
                    [attr.aria-label]="'Articles de la facture ' + invoice.invoiceNumber"
                    (click)="openDetail(invoice)"
                  >
                    Détail
                  </button>
                </td>
              }
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

      <!-- Same rows, phone layout. Hidden by CSS above the breakpoint. -->
      <ul class="inv-cards">
        @for (invoice of invoices; track invoice.id) {
          <li class="inv-card">
            <div class="head">
              <strong>{{ invoice.invoiceNumber }}</strong>
              @if (showDate) {
                <span class="muted">{{ invoice.invoiceDate | date: 'dd/MM HH:mm' }}</span>
              }
            </div>
            <div class="who">
              {{ invoice.clientName }}
              @if (showSeller) {
                <span class="muted">— {{ invoice.sale.seller.fullName }}</span>
              }
            </div>
            <div class="badges">
              <app-payment-status-badge [status]="invoice.paymentStatus" />
              <app-delivery-status-badge [status]="invoice.deliveryStatus" />
              <span class="muted">{{ invoice.itemCount }} article(s)</span>
            </div>
            <div class="foot">
              <span class="amount">{{ invoice.sale.totalAmount | ariary }}</span>
              @if (actionLabel && actionLabel(invoice); as label) {
                <button
                  class="btn small"
                  type="button"
                  [disabled]="busy"
                  (click)="action.emit(invoice)"
                >
                  {{ label }}
                </button>
              }
            </div>
            @if (showDetail) {
              <button class="lnk-detail" type="button" (click)="openDetail(invoice)">
                Voir les articles ›
              </button>
            }
          </li>
        }
      </ul>

      @if (detail(); as invoice) {
        <app-invoice-detail-dialog
          [invoice]="invoice"
          [showSeller]="showSeller"
          (closed)="closeDetail()"
        />
      }
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
  /** Lets a row open its articles in a dialog. On by default: every screen benefits from it. */
  @Input() showDetail = true;
  @Output() readonly action = new EventEmitter<Invoice>();

  /** The invoice whose articles are on screen, kept by id rather than by reference. */
  private readonly detailId = signal<number | null>(null);

  /** Read back from the current list, so a reload refreshes the open dialog. */
  protected detail(): Invoice | null {
    const id = this.detailId();
    return id === null ? null : (this.invoices.find((invoice) => invoice.id === id) ?? null);
  }

  protected openDetail(invoice: Invoice): void {
    this.detailId.set(invoice.id);
  }

  protected closeDetail(): void {
    this.detailId.set(null);
  }
}
