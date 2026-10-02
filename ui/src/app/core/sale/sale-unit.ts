import { Product } from '../models';

/**
 * A unit a product can be sold in: its base unit, or one of its packagings ("kg", "sac 50 kg").
 *
 * <p>Both answer the same questions — what it is called, how many base units it holds, what it
 * costs — so a cart line holds one of these and never needs to know which kind it is.
 */
export interface SaleUnit {
  /** Null for the base unit. */
  readonly packagingId: number | null;
  /** Null for the base unit of a product counted in bare units. */
  readonly label: string | null;
  /** Base units in one of these; 1 for the base unit. */
  readonly factor: number;
  readonly price: number;
}

/** The unit the stock is counted in, at the product's own price. */
export function baseUnitOf(product: Product): SaleUnit {
  return { packagingId: null, label: product.unit, factor: 1, price: product.price };
}

/** Every unit the product sells in, the base one first, then the packagings smallest first. */
export function unitsOf(product: Product): SaleUnit[] {
  return [
    baseUnitOf(product),
    ...(product.packagings ?? []).map((packaging) => ({
      packagingId: packaging.id,
      label: packaging.label,
      factor: packaging.factor,
      price: packaging.price,
    })),
  ];
}

/** The packaging this code was scanned from — a sack has its own barcode — or null. */
export function unitByBarcode(product: Product, code: string): SaleUnit | null {
  const packaging = product.packagings?.find((candidate) => candidate.barcode === code);
  return packaging ? (unitsOf(product).find((unit) => unit.packagingId === packaging.id) ?? null) : null;
}

export function sameUnit(a: SaleUnit, b: SaleUnit): boolean {
  return a.packagingId === b.packagingId;
}

/**
 * To the thousandth, the precision the server stores. Floating point turns 0.1 × 3.5 into
 * 0.35000000000000003; every quantity the cart computes goes through here.
 */
export function roundQuantity(value: number): number {
  return Math.round(value * 1000) / 1000;
}

/** Rounded down to the thousandth: the most of a unit that still fits on the shelf. */
export function floorQuantity(value: number): number {
  return Math.floor(value * 1000 + 1e-6) / 1000;
}

/** As a person writes it: "3", "1,75", "0,5". */
export function formatQuantity(value: number): string {
  return value.toLocaleString('fr-FR', { maximumFractionDigits: 3 });
}

/** "2 kg", "3 sac 50 kg", or a bare "3" for a product counted in bare units. */
export function formatAmount(quantity: number, label: string | null): string {
  return label ? `${formatQuantity(quantity)} ${label}` : formatQuantity(quantity);
}

/**
 * A stock figure in the unit that reads best: the largest one it holds at least one of. 1 743,5
 * kapoka reads "9,96 sac 50 kg", 30 kapoka "8,57 kg", 2 kapoka "2 kapoka" — the way a
 * shopkeeper says it. A product with a single unit keeps it.
 *
 * @param quantity in base units; the product's stock by default
 */
export function stockLabel(product: Product, quantity: number = product.stockQuantity): string {
  const units = unitsOf(product);
  const best = [...units].reverse().find((unit) => quantity >= unit.factor) ?? units[0];
  const inBest = best.factor === 1 ? quantity : Math.round((quantity / best.factor) * 100) / 100;
  return formatAmount(roundQuantity(inBest), best.label);
}

/**
 * Reads a typed quantity: "3", "1,5", "0.25". Null for anything else, and for zero or less —
 * a line is removed with its button, not by typing 0.
 */
export function parseQuantity(text: string): number | null {
  const cleaned = text.replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,3})?$/.test(cleaned)) {
    return null;
  }
  const value = Number(cleaned);
  return value > 0 ? value : null;
}
