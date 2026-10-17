import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { StockApi } from '../api/stock.api';
import { StockAdjustment, Supply } from '../models';
import { ToastService } from '../services/toast.service';

/**
 * The message after a receipt or a correction, with « Annuler » for the few seconds it stays: a
 * wrong quantity is put right where it was typed, without looking for it in the history.
 *
 * <p>« Annuler » writes the opposite movement, as the « ⋮ » of a past receipt does: nothing is
 * deleted, the history shows both. The stock shown everywhere follows by itself; the screens
 * listing receipts or corrections reload on {@link undone$}.
 */
@Injectable({ providedIn: 'root' })
export class StockUndo {
  private readonly stock = inject(StockApi);
  private readonly toasts = inject(ToastService);
  private readonly undone = new Subject<void>();

  /** A receipt or a correction was just taken back from the message. */
  readonly undone$: Observable<void> = this.undone.asObservable();

  /** After a receipt, or a delivery note of several: « Annuler » takes them all back, or none. */
  received(message: string, supplies: readonly Supply[]): void {
    const ids = supplies.map((supply) => supply.id);
    const what = ids.length === 1 ? 'Réception annulée' : `Réception de ${ids.length} produits annulée`;
    this.offer(
      message,
      () => (ids.length === 1 ? this.stock.cancelSupply(ids[0]) : this.stock.cancelDelivery(ids)),
      `${what} : le stock est revenu comme avant.`,
    );
  }

  /** After a correction by hand. */
  corrected(message: string, adjustment: StockAdjustment): void {
    this.offer(
      message,
      () => this.stock.cancelAdjustment(adjustment.id),
      'Correction annulée : le stock est revenu comme avant.',
    );
  }

  private offer(message: string, cancel: () => Observable<unknown>, done: string): void {
    this.toasts.show(message, [
      {
        label: 'Annuler',
        // A refusal (the goods already sold) is said by the error message.
        run: () =>
          cancel().subscribe(() => {
            this.toasts.show(done);
            this.undone.next();
          }),
      },
    ]);
  }
}
