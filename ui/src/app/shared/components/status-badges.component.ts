import { Component, Input } from '@angular/core';
import { DeliveryStatus, PaymentStatus, RemittanceStatus, UserApp } from '../../core/models';

@Component({
  selector: 'app-payment-status-badge',
  standalone: true,
  template: `
    @if (status === PaymentStatus.PAID) {
      <span class="badge green">Payée</span>
    } @else {
      <span class="badge amber">Non payée</span>
    }
  `,
})
export class PaymentStatusBadgeComponent {
  @Input({ required: true }) status!: PaymentStatus;
  protected readonly PaymentStatus = PaymentStatus;
}

@Component({
  selector: 'app-delivery-status-badge',
  standalone: true,
  template: `
    @if (status === DeliveryStatus.DELIVERED) {
      <span class="badge grey">Remis</span>
    } @else {
      <span class="badge green">À remettre</span>
    }
  `,
})
export class DeliveryStatusBadgeComponent {
  @Input({ required: true }) status!: DeliveryStatus;
  protected readonly DeliveryStatus = DeliveryStatus;
}

@Component({
  selector: 'app-remittance-status-badge',
  standalone: true,
  template: `
    @if (status === RemittanceStatus.CONFIRMED) {
      <span class="badge green">Confirmé{{ confirmedBy ? ' par ' + confirmedBy.fullName : '' }}</span>
    } @else {
      <span class="badge amber">En attente</span>
    }
  `,
})
export class RemittanceStatusBadgeComponent {
  @Input({ required: true }) status!: RemittanceStatus;
  @Input() confirmedBy: UserApp | null = null;
  protected readonly RemittanceStatus = RemittanceStatus;
}

/**
 * The alert state comes from the server's `lowStock` flag rather than from a threshold
 * repeated here, so raising the reorder level is a backend change only.
 */
@Component({
  selector: 'app-stock-status-badge',
  standalone: true,
  template: `
    @if (quantity <= 0) {
      <span class="badge red">Épuisé</span>
    } @else if (lowStock) {
      <span class="badge red">Stock bas</span>
    } @else {
      <span class="badge green">OK</span>
    }
  `,
})
export class StockStatusBadgeComponent {
  @Input({ required: true }) quantity!: number;
  @Input({ required: true }) lowStock!: boolean;
}
