import { parseDecimal } from '../catalog/decimal-text';
import { Product } from '../models';
import { roundQuantity } from '../sale/sale-unit';

/** One product found different from its stock: what the inventory will correct. */
export interface InventoryGap {
  readonly product: Product;
  readonly counted: number;
  /** counted − stock, in base units: −2 when two are missing. */
  readonly difference: number;
  /** The difference at the sale price, for "−9 600 Ar": what the gap weighs. */
  readonly value: number;
}

/** What a count of a rayon comes to, said before anything is written. */
export interface InventorySummary {
  /** Every product counted, found right or not. */
  readonly counted: number;
  readonly gaps: InventoryGap[];
  /** Products left blank: not touched. */
  readonly skipped: number;
  /** Counts that cannot be read ("3,5,2", letters): to fix before saving. */
  readonly unreadable: Product[];
}

/** A count as typed, to the thousandth; empty is "not counted", which is not zero. */
export function readCount(text: string): number | null {
  const count = parseDecimal(text);
  return count === null || Math.round(count * 1000) !== count * 1000 ? null : count;
}

/** The summary shown before saving: the gaps, the products found right, those left blank. */
export function summarize(products: readonly Product[], typed: ReadonlyMap<number, string>): InventorySummary {
  const gaps: InventoryGap[] = [];
  const unreadable: Product[] = [];
  let counted = 0;
  let skipped = 0;
  for (const product of products) {
    const text = (typed.get(product.id) ?? '').trim();
    if (text === '') {
      skipped++;
      continue;
    }
    const count = readCount(text);
    if (count === null) {
      unreadable.push(product);
      continue;
    }
    counted++;
    const difference = roundQuantity(count - product.stockQuantity);
    if (difference !== 0) {
      gaps.push({ product, counted: count, difference, value: Math.round(difference * product.price) });
    }
  }
  return { counted, gaps, skipped, unreadable };
}
