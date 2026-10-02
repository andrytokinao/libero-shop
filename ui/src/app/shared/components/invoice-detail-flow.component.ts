import { Component, computed, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { Invoice, PaymentMethod } from '../../core/models';
import { asksHowToPay } from '../../core/orders/delivery.util';
import { InvoiceActionRunner } from '../../core/orders/invoice-action-runner.service';
import {
  InvoiceAction,
  InvoiceActionKind,
  availableActions,
} from '../../core/orders/invoice-actions';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceStore } from '../../core/store/invoice.store';
import { CancelOrderDialogComponent } from './cancel-order-dialog.component';
import { HandOverDialogComponent } from './hand-over-dialog.component';
import { InvoiceDetailDialogComponent } from './invoice-detail-dialog.component';
import { PayDialogComponent } from './pay-dialog.component';

/**
 * An order's detail with everything one can do to it from there: the buttons of
 * `availableActions`, the dialogs some of them need first (how the customer pays, why the order
 * is cancelled), and the call itself.
 *
 * <p>One place for the whole of it, opened from a list of orders as from the till right after a
 * sale — a Mediator between the detail and its action dialogs, so neither the list nor the till
 * knows how printing, serving or cashing an order goes.
 *
 * <p>The order is read live from the {@link InvoiceStore}: once an action succeeds, the store
 * holds the order's new state and the detail shows it, with the buttons of that new state.
 */
@Component({
  selector: 'app-invoice-detail-flow',
  standalone: true,
  imports: [
    InvoiceDetailDialogComponent,
    CancelOrderDialogComponent,
    PayDialogComponent,
    HandOverDialogComponent,
  ],
  template: `
    @let order = current();
    @switch (step()) {
      @case ('cancel') {
        <app-cancel-order-dialog
          [invoice]="order"
          [busy]="running()"
          (confirmed)="run(runner.cancel(order, $event))"
          (closed)="step.set(null)"
        />
      }
      @case ('pay') {
        <app-pay-dialog
          [invoice]="order"
          [busy]="running()"
          (paid)="run(runner.pay(order, $event))"
          (closed)="step.set(null)"
        />
      }
      @case ('collect') {
        <app-pay-dialog
          [invoice]="order"
          [busy]="running()"
          [methods]="cashOnly"
          (paid)="run(runner.collect(order))"
          (closed)="step.set(null)"
        />
      }
      @case ('handOver') {
        <app-hand-over-dialog
          [invoice]="order"
          [busy]="running()"
          (chosen)="run(runner.handOver(order, $event))"
          (closed)="step.set(null)"
        />
      }
      @default {
        <app-invoice-detail-dialog
          [invoice]="order"
          [showSeller]="showSeller()"
          [notice]="notice()"
          [actions]="actions()"
          [busy]="running()"
          (act)="act(order, $event)"
          (closed)="closed.emit()"
        />
      }
    }
  `,
})
export class InvoiceDetailFlowComponent {
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  private readonly store = inject(InvoiceStore);
  protected readonly runner = inject(InvoiceActionRunner);

  /** The order to show; followed in the store from then on. */
  readonly invoice = input.required<Invoice>();
  readonly showSeller = input(true);
  /** Offers "Annuler la commande" where the rules allow it. Off where orders are only served. */
  readonly allowCancel = input(false);
  /** A line above the detail — "Vente enregistrée" right after the till recorded it. */
  readonly notice = input<string | null>(null);

  readonly closed = output<void>();
  /** An action went through: the order moved. */
  readonly changed = output<void>();

  /** The dialog an action opens over the detail to ask what it needs, or null for the detail. */
  protected readonly step = signal<'pay' | 'collect' | 'handOver' | 'cancel' | null>(null);
  protected readonly running = signal(false);
  protected readonly cashOnly = [PaymentMethod.CASH];

  /** The order as the store has it now, or as it was handed in until the store knows it. */
  protected readonly current = computed(
    () => this.store.entities().get(this.invoice().id) ?? this.invoice(),
  );

  /** The detail's buttons for this person — see `availableActions` for the rules. */
  protected readonly actions = computed<InvoiceAction[]>(() =>
    availableActions(this.current(), {
      userId: this.auth.currentUser()?.id ?? null,
      roles: this.auth.roles(),
      features: this.auth.settings(),
      handOverLabel: this.auth.words().handOverAction,
    }).filter((action) => action.kind !== InvoiceActionKind.CANCEL || this.allowCancel()),
  );

  /** A button of the detail: straight to the server, or through the dialog that asks first. */
  protected act(invoice: Invoice, action: InvoiceAction): void {
    switch (action.kind) {
      case InvoiceActionKind.PAY:
        return this.step.set('pay');
      case InvoiceActionKind.COLLECT:
        return this.step.set('collect');
      case InvoiceActionKind.CANCEL:
        return this.step.set('cancel');
      case InvoiceActionKind.HAND_OVER:
        return asksHowToPay(invoice, this.auth.settings().payAtDepot)
          ? this.step.set('handOver')
          : this.run(this.runner.handOver(invoice, true));
      case InvoiceActionKind.REMIT_CASH:
        return this.run(this.runner.remitCash(invoice));
      case InvoiceActionKind.CONFIRM_REMITTANCE:
        return this.run(this.runner.confirmRemittance(invoice));
      case InvoiceActionKind.PRINT:
        return this.run(this.runner.print(invoice));
    }
  }

  /**
   * Runs one action and says what happened. The detail stays open on the order in its new
   * state; a refusal is told by the error interceptor, and the person stays where they were.
   */
  protected run(work: Observable<string>): void {
    this.running.set(true);
    work.subscribe({
      next: (message) => {
        this.running.set(false);
        this.step.set(null);
        this.toasts.show(message);
        this.changed.emit();
      },
      error: () => this.running.set(false),
    });
  }
}
