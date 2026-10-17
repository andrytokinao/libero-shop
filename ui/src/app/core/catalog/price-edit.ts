import { parseDecimal } from './decimal-text';

/** The price being typed in the "Prix et unités" table: which unit of which product, and the text. */
export interface PriceEdit {
  readonly productId: number;
  /** Null for the base unit. */
  readonly packagingId: number | null;
  readonly text: string;
}

/** "12 500" → 12500. Null for what the server would refuse: empty, zero, more than two decimals. */
export function typedPrice(text: string): number | null {
  const price = parseDecimal(text);
  if (price === null || price <= 0 || Math.round(price * 100) !== price * 100) {
    return null;
  }
  return price;
}

/**
 * The profit on one unit at the price being typed, worked out like the server does it — what is
 * shown beside the field before Entrée. Null while the cost or the price is unknown.
 */
export function marginAt(price: number | null, cost: number | null): { margin: number; rate: number } | null {
  if (price === null || cost === null) {
    return null;
  }
  const margin = price - cost;
  return { margin, rate: Math.round((margin / price) * 1000) / 10 };
}
