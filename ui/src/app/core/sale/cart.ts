import { Signal, computed, signal } from '@angular/core';
import { CreateSaleRequest, Product } from '../models';
import {
  SaleUnit,
  baseUnitOf,
  floorQuantity,
  formatAmount,
  roundQuantity,
  sameUnit,
} from './sale-unit';

export interface CartLine {
  readonly product: Product;
  /** What it is sold in: the base unit, or "kg", "sac"… — priced as that unit. */
  readonly unit: SaleUnit;
  /** In {@link unit}, to the thousandth: 0.5 for half a kilo. */
  readonly quantity: number;
}

/** One product in one unit: "1 sac" and "3 kg" of the same rice are two lines. */
function keyOf(productId: number, unit: SaleUnit): string {
  return `${productId}:${unit.packagingId ?? 'base'}`;
}

function baseQuantityOf(line: CartLine): number {
  return roundQuantity(line.quantity * line.unit.factor);
}

/**
 * What a customer is buying, before it becomes a sale.
 *
 * <p>Each line keeps the product it was added with rather than an id to resolve against a
 * catalogue list. A scanned article is not necessarily on screen — the list is filtered, and
 * will soon be fetched a page at a time — so the cart must not depend on what the screen
 * happens to hold. Lines stay in the order they were added, which is the order a till reads.
 *
 * <p>A line is one product in one unit. The stock is shared, though: every check — what is
 * left, whether a line is short, how far a line may grow — adds up the product's lines in base
 * units, because "1 sac" and "3 kg" of the same rice come off one shelf.
 *
 * <p>A plain class rather than a service: every screen that sells owns its own cart, and two
 * of them open at once must never share one.
 */
export class Cart {
  private readonly entries = signal<ReadonlyMap<string, CartLine>>(new Map());

  readonly lines: Signal<readonly CartLine[]> = computed(() => [...this.entries().values()]);
  readonly isEmpty = computed(() => this.entries().size === 0);
  /** Quantities added up, each in its own unit — a size for "5 article(s)", not a stock figure. */
  readonly units = computed(() =>
    roundQuantity(this.lines().reduce((sum, line) => sum + line.quantity, 0)),
  );
  readonly total = computed(() => this.lines().reduce((sum, line) => sum + lineTotal(line), 0));

  /**
   * The lines asking for more than the shelf holds — known once the server has refused the
   * sale and {@link applyStock} has brought the real figures in. Derived, not flagged: a line
   * stops being short the moment its quantity is brought down, with nothing to reset.
   */
  readonly shortLines = computed(() => this.lines().filter((line) => this.isShort(line.product)));
  readonly hasShortage = computed(() => this.shortLines().length > 0);

  /** What the cart takes of this product, in base units, across its lines. */
  quantityOf(product: Product): number {
    return roundQuantity(
      this.linesOf(product.id).reduce((sum, line) => sum + baseQuantityOf(line), 0),
    );
  }

  /**
   * What a product tile's badge says is in the basket: "3", or "2 kg" — every line of the
   * product in its own unit, named when the product sells in several.
   */
  countLabelOf(product: Product): string {
    const named = (product.packagings?.length ?? 0) > 0;
    return this.linesOf(product.id)
      .map((line) => formatAmount(line.quantity, named ? line.unit.label : null))
      .join(' + ');
  }

  /** The product's lines, in the order they were added. */
  linesOf(productId: number): CartLine[] {
    return this.lines().filter((line) => line.product.id === productId);
  }

  /** Whether this product's lines together ask for more than the shelf holds. */
  isShort(product: Product): boolean {
    const lines = this.linesOf(product.id);
    return lines.length > 0 && this.quantityOf(product) > lines[0].product.stockQuantity;
  }

  /** What is still on the shelf once this cart has taken its share, in base units — never below zero. */
  remainingStock(product: Product): number {
    return Math.max(0, roundQuantity(product.stockQuantity - this.quantityOf(product)));
  }

  /**
   * Brings in the stock the server found when it refused the sale, in base units. The
   * quantities are left as the cashier entered them — the lines turn short, and it is for the
   * cashier to decide what to drop. A "−" on a short line brings it straight down to what fits.
   */
  applyStock(stock: readonly { productId: number; available: number }[]): void {
    if (!stock.length) {
      return;
    }
    this.entries.update((current) => {
      const updated = new Map(current);
      for (const { productId, available } of stock) {
        for (const [key, line] of updated) {
          if (line.product.id === productId) {
            updated.set(key, { ...line, product: { ...line.product, stockQuantity: available } });
          }
        }
      }
      return updated;
    });
  }

  /**
   * Adds up to `quantity` of `unit`, never more than the shelf holds.
   *
   * <p>A whole quantity is added in whole units: two sacks asked of a shelf holding one and a
   * half add one sack, never "1,5 sac" nobody asked for. Only a quantity typed with decimals is
   * filled to the thousandth.
   *
   * @param unit what it is sold in; by default the unit of the product's last line — a cashier
   *   who switched the rice to "kg" keeps adding kilos — or its base unit
   * @returns how many were actually added — less than asked, or 0, when the stock ran out,
   *   which the caller has to tell the cashier about
   */
  add(product: Product, quantity = 1, unit: SaleUnit = this.currentUnitOf(product)): number {
    const before = this.entries().get(keyOf(product.id, unit))?.quantity ?? 0;
    const room = this.fits(product, unit, [unit]) - before;
    const added = Math.max(
      0,
      Math.min(quantity, Number.isInteger(quantity) ? Math.floor(room + 1e-9) : floorQuantity(room)),
    );
    if (added > 0) {
      this.put(product, unit, roundQuantity(before + added));
    }
    return roundQuantity(added);
  }

  /** Moves the product's last line by `delta` — the tile's "−", F8. */
  change(product: Product, delta: number): void {
    const line = this.linesOf(product.id).at(-1);
    if (line) {
      this.changeLine(line, delta, product);
    } else if (delta > 0) {
      this.add(product, delta);
    }
  }

  /**
   * Moves a line by `delta`: up in whole steps as far as the shelf allows ({@link add}), down
   * to what the shelf holds at most — a "−" on a short line brings it straight to what fits. A
   * line brought to zero is removed.
   *
   * @param product the freshest figures for the product, kept as the line's snapshot
   */
  changeLine(line: CartLine, delta: number, product: Product = line.product): void {
    if (delta > 0) {
      this.add(product, delta, line.unit);
    } else {
      this.put(product, line.unit, roundQuantity(line.quantity + delta));
    }
  }

  /** Sets a line to a typed quantity, capped by the stock. */
  setLineQuantity(line: CartLine, quantity: number): void {
    this.put(line.product, line.unit, roundQuantity(quantity));
  }

  /**
   * Sells this line in another unit, keeping the number typed: "2 kapoka" becomes "2 kg", in the
   * same place on the ticket. Joins the product's line already in that unit, if there is one,
   * rather than making a second — and like any change, never beyond what the shelf holds.
   */
  setLineUnit(line: CartLine, unit: SaleUnit): void {
    if (sameUnit(line.unit, unit)) {
      return;
    }
    const oldKey = keyOf(line.product.id, line.unit);
    const newKey = keyOf(line.product.id, unit);
    const merged = roundQuantity((this.entries().get(newKey)?.quantity ?? 0) + line.quantity);
    const next = Math.min(merged, this.fits(line.product, unit, [line.unit, unit]));
    this.entries.update((current) => {
      const updated = new Map<string, CartLine>();
      for (const [key, value] of current) {
        if (key === oldKey) {
          if (next > 0) {
            updated.set(newKey, { product: line.product, unit, quantity: next });
          }
        } else if (key !== newKey) {
          updated.set(key, value);
        }
      }
      return updated;
    });
  }

  /** The line added last — what "remove the last article" acts on. */
  readonly lastLine = computed(() => this.lines().at(-1) ?? null);

  clear(): void {
    this.entries.set(new Map());
  }

  /**
   * Replaces the content with `lines`, in their order — how a sale put on hold comes back. A
   * line held before units existed has none, and is read as the base unit.
   */
  restore(lines: readonly CartLine[]): void {
    this.entries.set(
      new Map(
        lines.map((line) => {
          const unit = line.unit ?? baseUnitOf(line.product);
          return [keyOf(line.product.id, unit), { ...line, unit }];
        }),
      ),
    );
  }

  /** The lines as the sale endpoint takes them. */
  toSaleLines(): CreateSaleRequest['lines'] {
    return this.lines().map((line) => ({
      productId: line.product.id,
      packagingId: line.unit.packagingId,
      quantity: line.quantity,
    }));
  }

  /** The unit the product's last line is in, or its base unit. */
  currentUnitOf(product: Product): SaleUnit {
    return this.linesOf(product.id).at(-1)?.unit ?? baseUnitOf(product);
  }

  /**
   * Writes one line at `wanted`, as far as the shelf allows once the product's other lines have
   * taken their share; at zero or less the line goes. A new line goes at the end, an existing
   * one keeps its place.
   */
  private put(product: Product, unit: SaleUnit, wanted: number): void {
    const key = keyOf(product.id, unit);
    const next = Math.min(wanted, this.fits(product, unit, [unit]));
    this.entries.update((current) => {
      const updated = new Map(current);
      if (next <= 0) {
        updated.delete(key);
      } else {
        updated.set(key, { product, unit, quantity: next });
      }
      return updated;
    });
  }

  /**
   * The most of `unit` the shelf can still give, once the product's lines in any other unit than
   * `replacing` have taken their share — rounded down to the thousandth.
   */
  private fits(product: Product, unit: SaleUnit, replacing: readonly SaleUnit[]): number {
    const others = this.linesOf(product.id)
      .filter((line) => !replacing.some((replaced) => sameUnit(line.unit, replaced)))
      .reduce((sum, line) => sum + baseQuantityOf(line), 0);
    return Math.max(0, floorQuantity((product.stockQuantity - others) / unit.factor));
  }
}

/** Price times quantity, to the ariary: what the line adds to the ticket. */
export function lineTotal(line: CartLine): number {
  return Math.round(line.unit.price * line.quantity * 100) / 100;
}
