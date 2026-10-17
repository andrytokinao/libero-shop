import { Supply } from '../models';

/** What was bought from one supplier, one line per product and unit: the last price, how often. */
export interface SupplierPurchase {
  readonly productName: string;
  /** The unit it came in; null for the product's base unit. */
  readonly unitLabel: string | null;
  readonly baseUnit: string | null;
  /** The last price paid for one of that unit, or null when no receipt said it. */
  readonly lastCost: number | null;
  readonly lastDate: string;
  readonly receipts: number;
}

/**
 * The receipts of one supplier, read as its price list: newest first in, so the first one seen of
 * each product and unit is the last price paid. Cancelled receipts are left out.
 */
export function purchasesFrom(supplies: readonly Supply[], supplierId: number): SupplierPurchase[] {
  const lines = new Map<string, SupplierPurchase>();
  const newestFirst = [...supplies].sort((a, b) => b.movementDate.localeCompare(a.movementDate));
  for (const supply of newestFirst) {
    if (supply.supplier?.id !== supplierId || supply.cancelledAt) {
      continue;
    }
    const key = `${supply.product.id}|${supply.unitLabel ?? ''}`;
    const known = lines.get(key);
    lines.set(
      key,
      known
        ? { ...known, receipts: known.receipts + 1, lastCost: known.lastCost ?? supply.unitCost }
        : {
            productName: supply.product.name,
            unitLabel: supply.unitLabel,
            baseUnit: supply.product.unit,
            lastCost: supply.unitCost,
            lastDate: supply.movementDate,
            receipts: 1,
          },
    );
  }
  // By product, then the base unit before its packagings.
  return [...lines.values()].sort(
    (a, b) =>
      a.productName.localeCompare(b.productName, 'fr') || (a.unitLabel ?? '').localeCompare(b.unitLabel ?? '', 'fr'),
  );
}
