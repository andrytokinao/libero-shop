import {Product} from '../../../core/models';

/** P = produit principal, C = produit secondaire, S = sous-produit. */
export type Role = 'P' | 'C' | 'S';
/** T = montant pour tout le lot, U = montant par unité. */
export type ChargeMode = 'T' | 'U';
/** N = perte normale (répartie sur le stock), A = perte anormale (sèche). */
export type LossType = 'N' | 'A';
/** V = répartition selon la valeur de vente, C = tout sur le produit principal. */
export type SplitMethod = 'V' | 'C';

/** Une unité du catalogue. Sans `of`, c'est une unité de référence (kg, litre, pièce…). */
export interface StockUnit {
  id: string;
  name: string;
  of: string | null;
  factor: number;
}

/** Une unité telle qu'un produit la voit : le facteur est converti en unité de référence. */
export interface ProductUnit {
  id: string;
  name: string;
  factor: number;
}

/** Vente dans une unité secondaire (ex. : en sac en plus du kg). */
export interface ExtraSale {
  on: boolean;
  qty: number | null;
  price: number | null;
}

/** Un produit (acheté ou obtenu) avec ses unités et son prix de vente. */


export interface Charge {
  label: string;
  amount: number | null;
  mode: ChargeMode;
}

export interface Loss {
  label: string;
  qty: number | null;
  unit: string;
  type: LossType;
}

export interface CostLine {
  label: string;
  amount: number | null;
}

export interface StockEntry {
  product: Product;
  buyPrice: number | null;
  charges: Charge[];
  losses: Loss[];
  transform: boolean;
  method: SplitMethod;
  productionCosts: CostLine[];
  outputs: Product[];
  saleCosts: Charge[];
}

/** Une ligne de prix d'un produit : une unité, son prix minimum et sa marge. */
export interface LineResult {
  unitId: string;
  unitName: string;
  isMain: boolean;
  remaining: number;
  minPrice: number;
  price: number;
  margin: number;
}

export interface ProductResult {
  sheet: Product;
  name: string;
  role: Role;
  stockLabel: string;
  over: boolean;
  lines: LineResult[];
  profit: number;
}

export interface EntryResult {
  totalCharges: number;
  totalCost: number;
  revenue: number;
  profit: number;
  saleCostTotal: number;
  alerts: string[];
  products: ProductResult[];
}

export interface SelectOption<T extends string = string> {
  v: T;
  l: string;
}
