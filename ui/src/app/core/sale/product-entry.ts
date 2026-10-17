import { Signal, computed, inject, signal } from '@angular/core';
import { Product } from '../models';
import { ToastService } from '../services/toast.service';
import { Cart } from './cart';
import type { ProductFinder } from './product-finder';
import { ProductScanner, ScanOutcome, describeScan } from './product-scanner.service';
import { baseUnitOf } from './sale-unit';
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
  /**
   * "Riz 5kg × 1 ajouté": the article that just went in as asked, said on a line under the field
   * rather than in a message over the bottom of the grid — it happens dozens of times a sale.
   * What needs reading (stock short, several matches) still comes as a message.
   */
  readonly lastAdded = signal<string | null>(null);
  /** The last code entered that no product carries, until the screen's dialog is closed. */
  readonly unknownCode = signal<string | null>(null);
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
      // A code no product carries is not a dead end: the screen offers what to do with it.
      if (outcome.kind === 'unknown') {
        this.unknownCode.set(outcome.code);
      } else {
        this.say(outcome);
      }
      // Left alone if the cashier has already started on the next article meanwhile.
      if (outcome.kind !== 'ambiguous' && this.text() === raw) {
        this.text.set('');
      }
    });
  }

  clear(): void {
    this.text.set('');
  }

  /** The article found by its name after an unknown code: one of it, in its base unit. */
  addPicked(product: Product): void {
    const unit = baseUnitOf(product);
    const added = this.cart.add(product, 1, unit);
    this.say(
      added > 0
        ? { kind: 'added', product, added, asked: 1, unit }
        : { kind: 'out-of-stock', product, unit },
    );
  }

  /** An article in as asked: the quiet line. Anything short of it: a message to read. */
  private say(outcome: ScanOutcome): void {
    if (outcome.kind === 'added' && outcome.added >= outcome.asked) {
      this.lastAdded.set(describeScan(outcome));
    } else {
      this.lastAdded.set(null);
      this.toasts.show(describeScan(outcome));
    }
  }
}
