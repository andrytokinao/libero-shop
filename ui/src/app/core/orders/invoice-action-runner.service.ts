import { Injectable, inject } from '@angular/core';
import { Observable, map, throwError } from 'rxjs';
import { InvoiceStore } from '../store/invoice.store';
import { describeSlip } from './remittance';
import { RemittanceStore } from '../store/remittance.store';
import { CancelInvoiceRequest, DeliveryStatus, Invoice, PaymentMethod } from '../models';

const amount = (value: number) => `${value.toLocaleString('fr-FR')} Ar`;

/**
 * Carries out the actions of an order's detail and says, in the person's words, what happened.
 *
 * <p>Every method answers the sentence to show once the server has agreed — the screens only
 * toast it: the stores carry the order's new state to every screen. The choices some actions need first (how the customer pays, why the
 * order is cancelled) are asked by the screen's dialogs before calling here.
 */
@Injectable({ providedIn: 'root' })
export class InvoiceActionRunner {
  private readonly invoices = inject(InvoiceStore);
  private readonly remittances = inject(RemittanceStore);

  pay(invoice: Invoice, paymentMethod: PaymentMethod): Observable<string> {
    return this.invoices
      .pay(invoice.id, { paymentMethod })
      .pipe(map((paid) => `Facture ${paid.invoiceNumber} encaissée — ${amount(paid.sale.totalAmount)}.`));
  }

  collect(invoice: Invoice): Observable<string> {
    return this.invoices
      .collect(invoice.id)
      .pipe(
        map((collected) => `${amount(collected.sale.totalAmount)} encaissés — à remettre à la caisse.`),
      );
  }

  take(invoice: Invoice): Observable<string> {
    return this.invoices
      .take(invoice.id)
      .pipe(map((taken) => `Commande ${taken.invoiceNumber} : vous vous en occupez — les autres la voient prise.`));
  }

  release(invoice: Invoice): Observable<string> {
    return this.invoices
      .release(invoice.id)
      .pipe(map((released) => `Commande ${released.invoiceNumber} remise dans la file.`));
  }

  /** @param collect the customer pays now, in cash, to whoever hands the order over */
  handOver(invoice: Invoice, collect: boolean): Observable<string> {
    return this.invoices
      .deliver(invoice.id, collect)
      .pipe(
        map((result) =>
          result.collected > 0
            ? `Commande ${invoice.invoiceNumber} remise — ${amount(result.collected)} encaissés.`
            : `Commande ${invoice.invoiceNumber} remise.`,
        ),
      );
  }

  /** This order's cash only: the rest of what the person holds stays in hand. */
  remitCash(invoice: Invoice): Observable<string> {
    return this.remittances.submit([invoice.id]).pipe(map(describeSlip));
  }

  confirmRemittance(invoice: Invoice): Observable<string> {
    const remittanceId = invoice.cashTrail?.remittanceId;
    if (remittanceId == null) {
      return throwError(
        () => new Error(`La facture ${invoice.invoiceNumber} n'a pas de versement à confirmer.`),
      );
    }
    return this.remittances
      .confirm(remittanceId)
      .pipe(map((slip) => `Versement V-${slip.id} confirmé — ${amount(slip.amount)} en caisse.`));
  }

  print(invoice: Invoice): Observable<string> {
    return this.invoices
      .print(invoice.id)
      .pipe(map((printed) => `Facture ${printed.invoiceNumber} envoyée à l'impression.`));
  }

  cancel(invoice: Invoice, request: CancelInvoiceRequest): Observable<string> {
    return this.invoices
      .cancel(invoice.id, request)
      .pipe(
        map((cancelled) =>
          cancelled.deliveryStatus === DeliveryStatus.CANCELLED
            ? `Commande ${cancelled.invoiceNumber} annulée : articles remis en stock.`
            : `Commande ${cancelled.invoiceNumber} annulée (déjà remise, pas de retour en stock).`,
        ),
      );
  }
}
