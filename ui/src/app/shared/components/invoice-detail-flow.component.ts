import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { Invoice, PaymentMethod } from '../../core/models';
import { asksHowToPay } from '../../core/orders/delivery.util';
import { InvoiceActionRunner } from '../../core/orders/invoice-action-runner.service';
import {
  InvoiceAction,
  InvoiceActionKind,
  availableActions,
} from '../../core/orders/invoice-actions';
import { invoiceActionContext } from '../../core/orders/invoice-menu';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceStore } from '../../core/store/invoice.store';
import { CancelOrderDialogComponent } from './cancel-order-dialog.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';
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
    ConfirmDialogComponent,
  ],
  template: `
    @let order = current();
    @switch (step()) {
      @case ('cancel') {
        <app-cancel-order-dialog
          [invoice]="order"
          [busy]="running()"
          (confirmed)="run(runner.cancel(order, $event))"
          (closed)="back()"
        />
      }
      @case ('pay') {
        <app-pay-dialog
          [invoice]="order"
          [busy]="running()"
          (paid)="run(runner.pay(order, $event))"
          (closed)="back()"
        />
      }
      @case ('collect') {
        <app-pay-dialog
          [invoice]="order"
          [busy]="running()"
          [methods]="cashOnly"
          (paid)="run(runner.collect(order))"
          (closed)="back()"
        />
      }
      @case ('handOver') {
        <app-hand-over-dialog
          [invoice]="order"
          [busy]="running()"
          (chosen)="run(runner.handOver(order, $event))"
          (closed)="back()"
        />
      }
      @case ('confirm') {
        <!-- From a line's "⋮": the question the detail asks on a second press, asked here. -->
        @if (confirming(); as action) {
          <app-confirm-dialog
            [title]="action.label + ' — ' + order.invoiceNumber"
            [message]="action.confirm!"
            [confirmLabel]="action.label"
            [busy]="running()"
            (confirmed)="act(order, action)"
            (closed)="back()"
          />
        }
      }
      @default {
        @if (!start()) {
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
    }
  `,
})
export class InvoiceDetailFlowComponent implements OnInit {
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
  /**
   * Picked in a line's "⋮": the action runs at once, without the detail, and the flow closes once
   * it is done or given up. Null opens the detail.
   */
  readonly start = input<InvoiceActionKind | null>(null);

  readonly closed = output<void>();
  /** An action went through: the order moved. */
  readonly changed = output<void>();

  /** The dialog an action opens over the detail to ask what it needs, or null for the detail. */
  protected readonly step = signal<'pay' | 'collect' | 'handOver' | 'cancel' | 'confirm' | null>(null);
  /** The action whose question is on screen, from a line's "⋮". */
  protected readonly confirming = signal<InvoiceAction | null>(null);
  protected readonly running = signal(false);
  /**
   * Gone before its call came back: the order left the list it was opened from — taken on, it is
   * no longer waiting — and the list closed it. The message is still said; nobody listens for more.
   */
  private gone = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.gone = true));
  }
  protected readonly cashOnly = [PaymentMethod.CASH];

  /** The order as the store has it now, or as it was handed in until the store knows it. */
  protected readonly current = computed(
    () => this.store.entities().get(this.invoice().id) ?? this.invoice(),
  );

  /** The detail's buttons for this person — see `availableActions` for the rules. */
  protected readonly actions = computed<InvoiceAction[]>(() =>
    availableActions(this.current(), invoiceActionContext(this.auth)).filter((action) => action.kind !== InvoiceActionKind.CANCEL || this.allowCancel()),
  );

  ngOnInit(): void {
    const kind = this.start();
    if (kind === null) {
      return;
    }
    const action = this.actions().find((candidate) => candidate.kind === kind);
    if (!action) {
      // The order moved on since the menu was drawn: the detail says where it stands.
      this.closed.emit();
    } else if (action.confirm) {
      this.confirming.set(action);
      this.step.set('confirm');
    } else {
      this.act(this.current(), action);
    }
  }

  /** A dialog given up: back to the detail, or out altogether when the "⋮" opened it. */
  protected back(): void {
    if (this.start()) {
      this.closed.emit();
    } else {
      this.step.set(null);
    }
  }

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
      case InvoiceActionKind.TAKE_OVER:
        return this.run(this.runner.take(invoice));
      case InvoiceActionKind.RELEASE:
        return this.run(this.runner.release(invoice));
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
        if (this.gone) {
          return;
        }
        this.changed.emit();
        if (this.start()) {
          this.closed.emit();
        }
      },
      error: () => {
        this.running.set(false);
        if (!this.gone && this.start() && this.step() === null) {
          this.closed.emit();
        }
      },
    });
  }
}
