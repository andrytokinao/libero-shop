import { Product } from '../models';
import { SaleUnit, formatAmount, roundQuantity } from '../sale/sale-unit';

/** "2 cartons" in base units: 24 bouteilles — what the stock moves by, as the server works it out. */
export function inBaseUnits(quantity: number, unit: SaleUnit): number {
  return roundQuantity(quantity * unit.factor);
}

/**
 * "2 carton = 24 bouteille", read back under the field when the quantity is said in a packaging;
 * null in the base unit, where there is nothing to convert.
 */
export function conversionLine(quantity: number | null, unit: SaleUnit, product: Product): string | null {
  if (quantity === null || unit.packagingId === null) {
    return null;
  }
  return `${formatAmount(quantity, unit.label)} = ${formatAmount(inBaseUnits(quantity, unit), product.unit)}`;
}
