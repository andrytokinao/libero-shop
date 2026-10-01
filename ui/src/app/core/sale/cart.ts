import { Signal, computed, signal } from '@angular/core';
import { CreateSaleRequest, Product } from '../models';

export interface CartLine {
  readonly product: Product;
  readonly quantity: number;
}

/**
 * What a customer is buying, before it becomes a sale.
 *
 * <p>Each line keeps the product it was added with rather than an id to resolve against a
 * catalogue list. A scanned article is not necessarily on screen — the list is filtered, and
 * will soon be fetched a page at a time — so the cart must not depend on what the screen
 * happens to hold. Lines stay in the order they were added, which is the order a till reads.
 *
 * <p>A plain class rather than a service: every screen that sells owns its own cart, and two
 * of them open at once must never share one.
 */
export class Cart {
  private readonly entries = signal<ReadonlyMap<number, CartLine>>(new Map());

  readonly lines: Signal<readonly CartLine[]> = computed(() => [...this.entries().values()]);
  readonly isEmpty = computed(() => this.entries().size === 0);
  readonly units = computed(() => this.lines().reduce((sum, line) => sum + line.quantity, 0));
  readonly total = computed(() =>
    this.lines().reduce((sum, line) => sum + line.product.price * line.quantity, 0),
  );

  quantityOf(product: Product): number {
    return this.entries().get(product.id)?.quantity ?? 0;
  }

  /** What is still on the shelf once this cart has taken its share. */
  remainingStock(product: Product): number {
    return product.stockQuantity - this.quantityOf(product);
  }

  /**
   * Adds up to `quantity`, never more than the shelf holds.
   *
   * @returns how many were actually added — less than asked, or 0, when the stock ran out,
   *   which the caller has to tell the cashier about
   */
  add(product: Product, quantity = 1): number {
    const before = this.quantityOf(product);
    this.change(product, quantity);
    return this.quantityOf(product) - before;
  }

  /** Moves a line by `delta`, capped by the stock; a line brought to zero is removed. */
  change(product: Product, delta: number): void {
    this.entries.update((current) => {
      const wanted = (current.get(product.id)?.quantity ?? 0) + delta;
      const next = Math.min(wanted, product.stockQuantity);
      const updated = new Map(current);
      if (next <= 0) {
        updated.delete(product.id);
      } else {
        updated.set(product.id, { product, quantity: next });
      }
      return updated;
    });
  }

  /** The line added last — what "remove the last article" acts on. */
  readonly lastLine = computed(() => this.lines().at(-1) ?? null);

  clear(): void {
    this.entries.set(new Map());
  }

  /** Replaces the content with `lines`, in their order — how a sale put on hold comes back. */
  restore(lines: readonly CartLine[]): void {
    this.entries.set(new Map(lines.map((line) => [line.product.id, line])));
  }

  /** The lines as the sale endpoint takes them. */
  toSaleLines(): CreateSaleRequest['lines'] {
    return this.lines().map((line) => ({ productId: line.product.id, quantity: line.quantity }));
  }
}
