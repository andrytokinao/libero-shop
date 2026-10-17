import { CartLine } from './cart';
import { SaleUnit, sameUnit, unitsOf } from './sale-unit';

/** "unit:12" for a packaging, "unit:base" for the base unit. */
const unitId = (unit: SaleUnit) => `unit:${unit.packagingId ?? 'base'}`;

/** One entry of a basket line's "⋮", as the shared action menu draws it. */
export interface CartLineMenuEntry {
  readonly id: string;
  readonly label: string;
  readonly group?: string;
  readonly danger?: boolean;
}

/**
 * The "⋮" of a basket line: what the till already allows by gesture — type the quantity, tap
 * another unit, bring it to zero — said in words for whoever does not know the gestures.
 */
export function cartLineMenu(line: CartLine): CartLineMenuEntry[] {
  const others = unitsOf(line.product).filter((unit) => !sameUnit(unit, line.unit));
  return [
    { id: 'quantity', label: 'Changer la quantité' },
    ...others.map((unit) => ({ id: unitId(unit), label: `Vendre en ${unit.label ?? 'unité'}`, group: 'Unité' })),
    { id: 'remove', label: 'Retirer du panier', danger: true },
  ];
}

/** The unit an entry of {@link cartLineMenu} names, or null for the other entries. */
export function unitOfEntry(line: CartLine, id: string): SaleUnit | null {
  return unitsOf(line.product).find((unit) => unitId(unit) === id) ?? null;
}
