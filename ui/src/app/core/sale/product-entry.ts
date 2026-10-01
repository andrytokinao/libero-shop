import { Signal, computed, inject, signal } from '@angular/core';
import { Product } from '../models';
import { ToastService } from '../services/toast.service';
import { Cart } from './cart';
import type { ProductFinder } from './product-finder';
import { ProductScanner, describeScan } from './product-scanner.service';
import { parseScanEntry } from './scan-entry';

/**
 * The product field of a screen that sells: a search box as you type, a till as you press
 * Enter.
 *
 * <p>Typing searches on {@link term} — the code part of the entry, so "3*riz" searches "riz"
 * (see {@link ProductFinder}). Enter hands the entry to the {@link ProductScanner}, and the field is
 * emptied for the next article unless the text named several products, in which case it stays
 * so the cashier can tap the right one.
 *
 * <p>Built in a field initializer of the component that owns the cart (it injects what it
 * needs), and kept out of the component so the till and the order-taking screen behave alike.
 */
export class ProductEntry {
  private readonly scanner = inject(ProductScanner);
  private readonly toasts = inject(ToastService);

  readonly text = signal('');
  readonly term: Signal<string> = computed(
    () => parseScanEntry(this.text())?.code.toLowerCase() ?? '',
  );

  /**
   * @param matches what the screen's search shows for {@link term} right now — the fallback
   *   when no product carries the entry as its code
   */
  constructor(
    private readonly cart: Cart,
    private readonly matches: () => readonly Product[],
  ) {}

  /**
   * @param raw what to enter: the field's text by default, or a code read elsewhere — by the
   *   phone's camera. The screen's search results only stand in for an unknown code when they
   *   are the results for that very text; a camera code must never fall back on the list
   *   some unrelated word in the field had narrowed.
   */
  submit(raw: string = this.text()): void {
    const entry = parseScanEntry(raw);
    if (!entry) {
      return;
    }
    const matches = raw === this.text() ? this.matches() : [];
    this.scanner.scan(entry, this.cart, matches).subscribe((outcome) => {
      this.toasts.show(describeScan(outcome));
      // Left alone if the cashier has already started on the next article meanwhile.
      if (outcome.kind !== 'ambiguous' && this.text() === raw) {
        this.text.set('');
      }
    });
  }

  clear(): void {
    this.text.set('');
  }
}
