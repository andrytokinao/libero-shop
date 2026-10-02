import { Component, Input } from '@angular/core';
import { Invoice } from '../../core/models';
import { formatAmount, formatQuantity } from '../../core/sale/sale-unit';
import { AriaryPipe } from '../pipes/ariary.pipe';

/**
 * The articles of an invoice, laid out as a picking list.
 *
 * <p>The depot hands orders over against this list: the storekeeper has to read what
 * leaves the shelves, not only what the customer owes, so the product and the quantity
 * come first and the money last. Rendered as a list rather than a table so it stays
 * readable inside a dialog and on a phone, where tables scroll sideways.
 */
@Component({
  selector: 'app-invoice-lines',
  standalone: true,
  imports: [AriaryPipe],
  template: `
    <div class="lines">
      <div class="lines-head">
        <span>Articles à vérifier</span>
        <span class="muted">
          {{ invoice.sale.lines.length }} référence(s) — {{ formatQuantity(invoice.itemCount) }} unité(s)
        </span>
      </div>
      <ul>
        @for (line of invoice.sale.lines; track line.id) {
          <li>
            <!-- In the unit it was sold in, as it was called then: "2 kg", "1 sac 50 kg". -->
            <span class="qty">{{ formatAmount(line.quantity, line.unitLabel) }}{{ line.unitLabel ? "" : "×" }}</span>
            <span class="name">{{ line.product.name }}</span>
            <span class="unit muted">{{ line.unitPrice | ariary }} / {{ line.unitLabel ?? "u" }}</span>
            <span class="sub">{{ line.unitPrice * line.quantity | ariary }}</span>
          </li>
        } @empty {
          <li class="muted">Aucun article sur cette facture.</li>
        }
      </ul>
      <div class="lines-total">
        <span>Total</span>
        <span>{{ invoice.sale.totalAmount | ariary }}</span>
      </div>
    </div>
  `,
})
export class InvoiceLinesComponent {
  @Input({ required: true }) invoice!: Invoice;

  protected readonly formatAmount = formatAmount;
  protected readonly formatQuantity = formatQuantity;
}
