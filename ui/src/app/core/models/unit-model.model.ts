/**
 * A set of units reused from product to product — "Poids": g, kg, tonne — as served by
 * UnitModelResponse. No price and no stock: only how its units relate to one another.
 */
export interface UnitModel {
  id: number;
  name: string;
  /** The unit the others are counted in: "g". */
  baseLabel: string;
  /** In the order they were added: each after the unit it is written in. */
  units: UnitModelUnit[];
}

export interface UnitModelUnit {
  id: number;
  label: string;
  /** How many `reference` units it holds: 1000 for the tonne written in kg. */
  quantity: number;
  /** Another unit of the model, or null for the model's reference unit. */
  reference: string | null;
  /** How many reference units of the model it holds: 1 000 000 for the tonne in g. */
  baseFactor: number;
}

/** A unit added to a model — mirrors UnitModelUnitRequest. */
export interface UnitModelUnitRequest {
  label: string;
  quantity: number;
  /** Another unit of the model, by name; null for its reference unit. */
  reference: string | null;
}

/** A new model — mirrors CreateUnitModelRequest. */
export interface CreateUnitModelRequest {
  name: string;
  baseLabel: string;
  units: UnitModelUnitRequest[];
}

/**
 * Units ticked in a model for one product, each at that product's price — mirrors
 * ApplyUnitModelRequest. The anchor ("1 kg = 3,5 kapoka") is only needed when the product does
 * not already sell in one of the model's units.
 */
export interface ApplyUnitModelRequest {
  modelId: number;
  anchorLabel: string | null;
  anchorQuantity: number | null;
  units: { label: string; price: number }[];
}
