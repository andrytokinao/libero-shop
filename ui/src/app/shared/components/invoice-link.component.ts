import { Component, inject, input, signal } from '@angular/core';
import { InvoiceApi } from '../../core/api/invoice.api';
import { Invoice } from '../../core/models';
import { InvoiceDetailDialogComponent } from './invoice-detail-dialog.component';

/**
 * An invoice number that opens the invoice: in the stock outputs, the cash handed over, a
 * product's history — wherever a number alone used to say "go and look for it elsewhere".
 *
 * <p>The invoice is read when the number is clicked, not before: a list of fifty outputs does not
 * fetch fifty invoices. Read only: what can be done to an order stays on the order screens.
 */
@Component({
  selector: 'app-invoice-link',
  standalone: true,
  imports: [InvoiceDetailDialogComponent],
  template: `
    <button
      type="button"
      class="inv-link"
      [title]="'Voir la facture ' + number()"
      [disabled]="loading()"
      (click)="open($event)"
    >
      {{ number() }}
    </button>
    @if (invoice(); as shown) {
      <app-invoice-detail-dialog [invoice]="shown" (closed)="invoice.set(null)" />
    }
  `,
  styles: `
    .inv-link {
      border: 0;
      padding: 0;
      background: none;
      color: var(--brand);
      font: inherit;
      text-decoration: underline;
      text-underline-offset: 2px;
      cursor: pointer;

      &:disabled {
        cursor: progress;
        opacity: 0.6;
      }
    }
  `,
})
export class InvoiceLinkComponent {
  private readonly api = inject(InvoiceApi);

  readonly invoiceId = input.required<number>();
  readonly number = input.required<string>();

  protected readonly invoice = signal<Invoice | null>(null);
  protected readonly loading = signal(false);

  protected open(event: MouseEvent): void {
    // A row that opens something of its own on click keeps it for the rest of the row.
    event.stopPropagation();
    this.loading.set(true);
    this.api.byId(this.invoiceId()).subscribe({
      next: (invoice) => {
        this.loading.set(false);
        this.invoice.set(invoice);
      },
      // A 403 or a 404 has been shown by the interceptor.
      error: () => this.loading.set(false),
    });
  }
}
