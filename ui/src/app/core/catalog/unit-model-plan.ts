import { UnitModel } from '../models';

/** What happens to one unit of a model if it is ticked for a product. */
export type PlannedStatus =
  /** It can be added: `factor` base units of the product. */
  | 'ok'
  /** It is the product's base unit already. */
  | 'base'
  /** The product already sells in it. */
  | 'present'
  /** Where the model meets the product is not known yet: the anchor is missing. */
  | 'no-anchor'
  /** More than three decimals of the base unit: a gram of rice counted in kapoka. */
  | 'too-precise'
  /** Exactly one base unit: the base unit again under another name. */
  | 'same-as-base';

export interface PlannedUnit {
  label: string;
  status: PlannedStatus;
  /** Base units of the product in one of it; null while unknown. */
  factor: number | null;
  /** The base price times the factor, rounded: a starting point, never imposed. */
  suggestedPrice: number | null;
}

export interface ModelPlan {
  /** True when the product shares no unit with the model and no anchor was given. */
  needsAnchor: boolean;
  /** The model's reference unit first, then its units in order. */
  units: PlannedUnit[];
}

/** The product as the plan needs it: its base unit and price, and the units it already has. */
export interface PlanProduct {
  baseUnit: string | null;
  basePrice: number | null;
  packagings: readonly { label: string; factor: number }[];
}

/** "1 kg = 3,5 kapoka": one of the model's units, in base units of the product. */
export interface Anchor {
  label: string;
  quantity: number;
}

/**
 * Previews what ticking a model's units would add to a product — the same reckoning as the
 * server's UnitModelApplication, so the screen can grey out what would be refused and propose a
 * price. The server still decides.
 *
 * <p>Where the model meets the product, in the server's order: the product is counted in one of
 * the model's units; else it already sells in one of them; else the anchor typed.
 */
export function planModel(model: UnitModel, product: PlanProduct, anchor: Anchor | null): ModelPlan {
  const factors = modelFactors(model);
  const perModelUnit = placement(factors, product, anchor);
  const owned = new Set(product.packagings.map((p) => fold(p.label)));
  const base = product.baseUnit === null ? null : fold(product.baseUnit);

  const units = [...factors.entries()].map(([folded, { label, factor: modelFactor }]): PlannedUnit => {
    if (folded === base) {
      return { label, status: 'base', factor: 1, suggestedPrice: null };
    }
    if (owned.has(folded)) {
      return { label, status: 'present', factor: null, suggestedPrice: null };
    }
    if (perModelUnit === null) {
      return { label, status: 'no-anchor', factor: null, suggestedPrice: null };
    }
    const factor = round(modelFactor * perModelUnit, 9);
    if (!hasAtMostThreeDecimals(factor)) {
      return { label, status: 'too-precise', factor, suggestedPrice: null };
    }
    if (factor === 1) {
      return { label, status: 'same-as-base', factor, suggestedPrice: null };
    }
    const suggestedPrice = product.basePrice ? Math.round(factor * product.basePrice) : null;
    return { label, status: 'ok', factor, suggestedPrice };
  });
  return { needsAnchor: perModelUnit === null, units };
}

/** Folded like the server's labels: "Sac 50 kg" and "sac50kg" are one name. */
export function fold(label: string): string {
  return label
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '');
}

/** Every label of the model, folded, with its reference units: the reference unit first. */
function modelFactors(model: UnitModel): Map<string, { label: string; factor: number }> {
  const factors = new Map<string, { label: string; factor: number }>();
  factors.set(fold(model.baseLabel), { label: model.baseLabel, factor: 1 });
  for (const unit of model.units) {
    factors.set(fold(unit.label), { label: unit.label, factor: unit.baseFactor });
  }
  return factors;
}

/** Base units of the product in one reference unit of the model; null while unknown. */
function placement(
  factors: Map<string, { factor: number }>,
  product: PlanProduct,
  anchor: Anchor | null,
): number | null {
  const base = product.baseUnit === null ? undefined : factors.get(fold(product.baseUnit));
  if (base) {
    return 1 / base.factor;
  }
  for (const [folded, { factor }] of factors) {
    const owned = product.packagings.find((p) => fold(p.label) === folded);
    if (owned) {
      return owned.factor / factor;
    }
  }
  const anchored = anchor && anchor.quantity > 0 ? factors.get(fold(anchor.label)) : undefined;
  return anchored ? anchor!.quantity / anchored.factor : null;
}

function round(value: number, decimals: number): number {
  const scale = 10 ** decimals;
  return Math.round(value * scale) / scale;
}

function hasAtMostThreeDecimals(value: number): boolean {
  return Math.abs(value * 1000 - Math.round(value * 1000)) < 1e-6;
}
