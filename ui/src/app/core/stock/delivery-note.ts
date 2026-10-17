import { CreateDeliveryRequest, Product, ProductCost } from '../models';
import { SaleUnit, unitsOf } from '../sale/sale-unit';

/** One line of a delivery note, as typed. Strings for the figures: an empty field is not zero. */
export interface DeliveryLine {
  readonly product: Product;
  /** The unit it came in; null for the base unit. */
  readonly packagingId: number | null;
  readonly quantity: string;
  /** Prefilled with the last price paid for that unit; empty when none is known. */
  readonly unitCost: string;
}

/** The last price paid for one unit of a product, as the cost field starts from; empty if unknown. */
export function lastCostText(cost: ProductCost | undefined, packagingId: number | null): string {
  const last = cost?.units.find((unit) => unit.packagingId === packagingId)?.lastCost;
  return last == null ? '' : String(Math.round(last));
}

/**
 * A product scanned or picked onto the note: one more of it when it is already there in that
 * unit — a scanner read twice is two cartons — else a new line, at the last price paid.
 */
export function addToNote(
  lines: readonly DeliveryLine[],
  product: Product,
  packagingId: number | null,
  cost: ProductCost | undefined,
): DeliveryLine[] {
  const same = lines.findIndex((line) => line.product.id === product.id && line.packagingId === packagingId);
  if (same >= 0) {
    return lines.map((line, i) =>
      i === same ? { ...line, quantity: String((readNumber(line.quantity) ?? 0) + 1) } : line,
    );
  }
  return [...lines, { product, packagingId, quantity: '1', unitCost: lastCostText(cost, packagingId) }];
}

/** The unit a line is counted in. */
export function unitOf(line: DeliveryLine): SaleUnit {
  return unitsOf(line.product).find((unit) => unit.packagingId === line.packagingId) ?? unitsOf(line.product)[0];
}

/** What a line costs, or null while its quantity or its price is unknown. */
export function lineTotal(line: DeliveryLine): number | null {
  const quantity = readNumber(line.quantity);
  const cost = readNumber(line.unitCost);
  return quantity === null || cost === null ? null : Math.round(quantity * cost);
}

/** The total of the lines whose price is known, and how many are without one. */
export function noteTotal(lines: readonly DeliveryLine[]): { amount: number; unpriced: number } {
  const totals = lines.map(lineTotal);
  return {
    amount: totals.reduce<number>((sum, total) => sum + (total ?? 0), 0),
    unpriced: totals.filter((total) => total === null).length,
  };
}

/** The note as the server takes it, or null while a quantity or a typed price is unreadable. */
export function deliveryRequest(supplierId: number | null, lines: readonly DeliveryLine[]): CreateDeliveryRequest | null {
  const converted: CreateDeliveryRequest['lines'] = [];
  for (const line of lines) {
    const quantity = readNumber(line.quantity);
    const typedCost = line.unitCost.trim();
    const unitCost = typedCost ? readNumber(typedCost) : null;
    if (quantity === null || quantity <= 0 || (typedCost && unitCost === null)) {
      return null;
    }
    converted.push({ productId: line.product.id, packagingId: line.packagingId, quantity, unitCost });
  }
  return converted.length ? { supplierId, lines: converted } : null;
}

/** "12", "1,5" → a number; anything else → null. */
function readNumber(text: string): number | null {
  const cleaned = text.replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d+)?$/.test(cleaned)) {
    return null;
  }
  return Number(cleaned);
}
