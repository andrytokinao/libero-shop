import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { RemittanceApi } from '../api/remittance.api';
import { CashRemittance, RemittanceStatus } from '../models';
import { AuthService } from '../services/auth.service';
import { EntityStore } from './entity-store';
import { InvoiceStore } from './invoice.store';
import { LiveList, liveList } from './live-list';

/** Mirrors the parameters of `GET /api/remittances`. */
export interface RemittanceQuery {
  readonly status?: RemittanceStatus;
  /** The slips the caller submitted. */
  readonly mine?: boolean;
}

/**
 * Every cash slip the browser knows, once — the depot's "En attente" and the cashier's inbox read
 * the same one, so a confirmation shows on both.
 */
@Injectable({ providedIn: 'root' })
export class RemittanceStore extends EntityStore<CashRemittance> {
  private readonly api = inject(RemittanceApi);
  private readonly auth = inject(AuthService);
  private readonly invoices = inject(InvoiceStore);

  /** Must be called in an injection context. */
  list(query: () => RemittanceQuery = () => ({})): LiveList<CashRemittance> {
    return liveList(this, {
      load: () => this.api.search(query()),
      matches: (slip) => this.matches(slip, query()),
      // The server's order: `remittanceDate desc, id desc`.
      compare: (a, b) =>
        a.remittanceDate !== b.remittanceDate
          ? a.remittanceDate < b.remittanceDate
            ? 1
            : -1
          : b.id - a.id,
    });
  }

  /** Brings cash to the desk: these orders', or everything held when none is given. */
  submit(invoiceIds: number[] = []): Observable<CashRemittance> {
    return this.api.submit(invoiceIds).pipe(tap((slip) => this.settled(slip)));
  }

  confirm(remittanceId: number): Observable<CashRemittance> {
    return this.api.confirm(remittanceId).pipe(tap((slip) => this.settled(slip)));
  }

  /** The slip as answered, and its orders read back: their payment status moved with it. */
  private settled(slip: CashRemittance): void {
    this.push([slip]);
    this.invoices.refresh(slip.invoices.map((invoice) => invoice.invoiceId));
  }

  private matches(slip: CashRemittance, query: RemittanceQuery): boolean {
    return (
      (!query.status || slip.status === query.status) &&
      (!query.mine || slip.submittedBy.id === this.auth.currentUser()?.id)
    );
  }
}
