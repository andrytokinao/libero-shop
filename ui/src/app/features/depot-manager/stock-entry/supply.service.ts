import { Injectable, inject } from '@angular/core';
import { StockApi } from '../../../core/api/stock.api';
import { Product, Supply } from '../../../core/models';
import { DeliveryLine } from '../../../core/stock/delivery-note';
import {firstValueFrom, Observable} from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api.config';
import { HttpClient } from '@angular/common/http';
import {ProductDialogRequest} from '../../../core/catalog/product-rights';
import {ProductDialogResult} from '../../../shared/services/product-dialogs.service';
import {
  StockEntryDialogChargeComponent
} from '../../../shared/components/stock-entry-charge-dialog/stock-entry-dialog-charge.component';
import {Dialog} from '@angular/cdk/dialog';

// ======================================================================= types

/** Base de calcul d'une charge : montant global du lot, ou montant par unité reçue. */
export type ChargeBasis = 'lot' | 'unit';

/**
 * Une charge saisie à l'onglet Charges.
 * - general = true  : montant total de la réception, réparti sur tous les produits ;
 * - general = false : s'applique à la ligne d'achat `lineIndex`, par lot ou par unité.
 */
export interface ChargeLine {
  label: string;
  amount: number | null;
  basis: ChargeBasis;
  general: boolean;
  lineIndex: number | null;
}

/** Charge telle que le backend la reçoit : une SupplyCharge liée à des Supply via SupplyUseCharge. */
export interface SupplyChargeRequest {
  raison: string;
  cust: number;
  basis: ChargeBasis;
  general: boolean;
  lineIndexes: number[];
}

export type LossReasonCode = 'TRANSPORT' | 'PROCESS' | 'SPOILAGE' | 'OTHER';

/** Une matière utilisée par la transformation : une ligne d'achat, et la quantité (dans son unité de réception). */
export interface MaterialUse {
  lineIndex: number | null;
  /** Quantité utilisée, pertes de fabrication comprises. */
  quantity: string;
}

/** Une perte, en quantité, sur une ligne d'achat (dans l'unité de réception de la ligne). */
export interface LossEntry {
  reason: LossReasonCode;
  lineIndex: number | null;
  quantity: string;
}

/** Un produit obtenu par la transformation. */
export interface OutputLine {
  product: Product;
  packagingId: number | null;
  quantity: string;
  /** Part du coût de la transformation, en %. */
  costShare: number | null;
}

export interface ProductionCostLine {
  label: string;
  amount: number | null;
}

/** Prix de vente d'un produit dans une unité, et si on le vend dans cette unité. */
export interface UnitPrice {
  on: boolean;
  price: string;
}

/** Clé : `${cardKey}|${packagingId ou 'base'}`. */
export type SalePrices = Record<string, UnitPrice>;

/** Un produit à vendre : une ligne d'achat restante (L) ou un produit obtenu (O). */
export interface SaleCard {
  key: string;
  kind: 'purchase' | 'output';
  index: number;
  product: Product;
  /** L'unité d'origine : celle de la réception ou de l'obtention. */
  packagingId: number | null;
  /** Quantité à vendre, dans l'unité d'origine. */
  quantity: number;
  /** Coût de revient estimé d'une unité d'origine, ou null s'il n'est pas connu. */
  cost: number | null;
}

/** Tout ce que l'utilisateur a saisi dans la fiche. */
export interface EntryState {
  lines: DeliveryLine[];
  charges: ChargeLine[];
  withLosses: boolean;
  losses: LossEntry[];
  withTransformation: boolean;
  materials: MaterialUse[];
  outputs: OutputLine[];
  productionCosts: ProductionCostLine[];
  sale: SalePrices;
}

type DeliveryRequest = Parameters<StockApi['registerDelivery']>[0];

// ------------------------------------------------- corps envoyé au backend

export interface LossRequest {
  reason: LossReasonCode;
  lineIndex: number | null;
  quantity: string;
}

export interface TransformationInputRequest {
  lineIndex: number | null;
  quantity: string;
}

export interface ProductionCostRequest {
  raison: string;
  cost: number;
}

export interface TransformationOutputRequest {
  index: number;
  productId: number;
  packagingId: number | null;
  quantity: string;
  costShare: number;
}

export interface TransformationRequest {
  inputs: TransformationInputRequest[];
  productionCosts: ProductionCostRequest[];
  outputs: TransformationOutputRequest[];
}

export interface UnitPriceRequest {
  packagingId: number | null;
  price: number;
}

export interface SalePriceRequest {
  source: 'LINE' | 'OUTPUT';
  index: number;
  productId: number;
  units: UnitPriceRequest[];
}

/** Corps de POST /stock/charge-deliveries : la livraison + charges, pertes, transformation, prix de vente. */
export type FullDeliveryRequest = DeliveryRequest & {
  charges: SupplyChargeRequest[];
  losses: LossRequest[];
  transformation: TransformationRequest | null;
  salePrices: SalePriceRequest[];
};

@Injectable({ providedIn: 'root' })
export class SupplyService {
  private readonly http = inject(HttpClient);

  readonly tabs: string[] = ['Achat', 'Charges', 'Pertes et transformation', 'Vente'];

  readonly chargePresets: { label: string }[] = [
    { label: 'Transport' },
    { label: 'Embarquement' },
    { label: 'Débarquement' },
    { label: 'Douane / taxes' },
    { label: 'Autre' },
  ];

  readonly productionCostPresets: { label: string }[] = [
    { label: 'Salaires' },
    { label: 'Énergie' },
    { label: 'Emballage' },
    { label: 'Autre' },
  ];

  readonly chargeBases: { v: ChargeBasis; l: string }[] = [
    { v: 'lot', l: 'Par lot (montant total)' },
    { v: 'unit', l: 'Par unité reçue' },
  ];

  readonly lossReasons: { v: LossReasonCode; l: string }[] = [
    { v: 'TRANSPORT', l: 'Perdu pendant le transport' },
    { v: 'PROCESS', l: 'Perdu pendant la fabrication' },
    { v: 'SPOILAGE', l: 'Casse / péremption' },
    { v: 'OTHER', l: 'Autre' },
  ];

  // ================================================================ utilitaires
  private dialog = inject(Dialog);

  /** Nombre saisi (virgule ou point) : le nombre s'il est positif, sinon 0. */
  positive(value: unknown): number {
    const n = Number(String(value ?? '').replace(',', '.').replace(/\s/g, ''));
    return Number.isFinite(n) && n > 0 ? n : 0;
  }

  quantityOf(line: DeliveryLine): number {
    return this.positive(line.quantity);
  }

  /** Prix d'achat de la ligne, ou null quand le prix unitaire n'est pas connu. */
  lineCost(line: DeliveryLine): number | null {
    const unit = this.positive(line.unitCost);
    return unit > 0 ? unit * this.quantityOf(line) : null;
  }

  // =================================================================== charges

  newCharge(label: string): ChargeLine {
    return { label, amount: null, basis: 'lot', general: true, lineIndex: null };
  }

  /** Base réellement appliquée : une charge générale est toujours un total (lot). */
  effectiveBasis(c: ChargeLine): ChargeBasis {
    return c.general ? 'lot' : c.basis;
  }

  chargeTotal(c: ChargeLine, lines: DeliveryLine[]): number {
    const amount = Number(c.amount) || 0;
    if (!amount) return 0;
    if (this.effectiveBasis(c) === 'lot') return amount;
    const line = c.lineIndex === null ? undefined : lines[c.lineIndex];
    return line ? amount * this.quantityOf(line) : 0;
  }

  isChargeValid(c: ChargeLine, lines: DeliveryLine[]): boolean {
    if (!(Number(c.amount) > 0)) return false;
    return c.general || (c.lineIndex !== null && !!lines[c.lineIndex]);
  }

  findIncomplete(charges: ChargeLine[], lines: DeliveryLine[]): ChargeLine | undefined {
    return charges.find((c) => c.amount !== null && !this.isChargeValid(c, lines));
  }

  sumCharges(charges: ChargeLine[], lines: DeliveryLine[]): number {
    return charges.reduce((s, c) => s + this.chargeTotal(c, lines), 0);
  }

  buildCharges(charges: ChargeLine[], lines: DeliveryLine[]): SupplyChargeRequest[] {
    return charges
      .filter((c) => this.isChargeValid(c, lines))
      .map((c) => ({
        raison: (c.label ?? '').trim() || 'Charge',
        cust: this.chargeTotal(c, lines),
        basis: this.effectiveBasis(c),
        general: c.general,
        lineIndexes: c.general ? lines.map((_, i) => i) : [c.lineIndex as number],
      }));
  }

  /** Part de chaque charge portée par chaque ligne : au prorata de la valeur d'achat, sinon des quantités. */
  chargeShares(s: EntryState): number[] {
    const costs = s.lines.map((l) => this.lineCost(l));
    const shares = s.lines.map(() => 0);
    const sumCost = costs.reduce<number>((a, x) => a + (x ?? 0), 0);
    const sumQty = s.lines.reduce((a, l) => a + this.quantityOf(l), 0);

    for (const c of s.charges) {
      if (!this.isChargeValid(c, s.lines)) continue;
      const total = this.chargeTotal(c, s.lines);
      if (c.general) {
        s.lines.forEach((l, i) => {
          const weight =
            sumCost > 0 ? (costs[i] ?? 0) / sumCost : sumQty > 0 ? this.quantityOf(l) / sumQty : 0;
          shares[i] += total * weight;
        });
      } else if (c.lineIndex !== null) {
        shares[c.lineIndex] += total;
      }
    }
    return shares;
  }

  // ============================================================ ré-indexation

  /** Quand une ligne d'achat est retirée : recale les charges qui la visaient. */
  onLineRemoved(charges: ChargeLine[], removedIndex: number): ChargeLine[] {
    return charges.map((c) => {
      if (c.general || c.lineIndex === null) return c;
      if (c.lineIndex === removedIndex) return { ...c, lineIndex: null };
      if (c.lineIndex > removedIndex) return { ...c, lineIndex: c.lineIndex - 1 };
      return c;
    });
  }

  /** Même chose pour les matières et les pertes. */
  reindex<T extends { lineIndex: number | null }>(list: T[], removedIndex: number): T[] {
    return list.map((e) => {
      if (e.lineIndex === null) return e;
      if (e.lineIndex === removedIndex) return { ...e, lineIndex: null } as T;
      if (e.lineIndex > removedIndex) return { ...e, lineIndex: e.lineIndex - 1 } as T;
      return e;
    });
  }

  /** Quand une ligne (L) ou un produit obtenu (O) disparaît : supprime ses prix et décale les suivants. */
  shiftSale(sale: SalePrices, prefix: 'L' | 'O', removedIndex: number): SalePrices {
    const out: SalePrices = {};
    for (const [key, value] of Object.entries(sale)) {
      const m = /^([LO])(\d+)\|(.*)$/.exec(key);
      if (!m || m[1] !== prefix) {
        out[key] = value;
        continue;
      }
      const i = Number(m[2]);
      if (i === removedIndex) continue;
      out[`${prefix}${i > removedIndex ? i - 1 : i}|${m[3]}`] = value;
    }
    return out;
  }

  // ======================================================= pertes et matières

  /** Pertes à prendre en compte : cochées, complètes, et sans « fabrication » hors transformation. */
  activeLosses(s: EntryState): LossEntry[] {
    if (!s.withLosses) return [];
    return s.losses.filter(
      (l) =>
        l.lineIndex !== null &&
        !!s.lines[l.lineIndex] &&
        this.positive(l.quantity) > 0 &&
        (s.withTransformation || l.reason !== 'PROCESS'),
    );
  }

  activeMaterials(s: EntryState): MaterialUse[] {
    if (!s.withTransformation) return [];
    return s.materials.filter(
      (m) => m.lineIndex !== null && !!s.lines[m.lineIndex] && this.positive(m.quantity) > 0,
    );
  }

  outputValid(s: EntryState, o: OutputLine): boolean {
    return s.withTransformation && this.positive(o.quantity) > 0;
  }

  /** Perdu sur une ligne, hors fabrication (la perte de fabrication est comprise dans la quantité utilisée). */
  lossOnLine(s: EntryState, lineIndex: number): number {
    return this.activeLosses(s)
      .filter((l) => l.lineIndex === lineIndex && l.reason !== 'PROCESS')
      .reduce((sum, l) => sum + this.positive(l.quantity), 0);
  }

  usedOnLine(s: EntryState, lineIndex: number, except?: MaterialUse): number {
    return this.activeMaterials(s)
      .filter((m) => m.lineIndex === lineIndex && m !== except)
      .reduce((sum, m) => sum + this.positive(m.quantity), 0);
  }

  /** Ce qui reste à vendre d'une ligne : reçu − perdu − utilisé, dans l'unité de la ligne. */
  remainingQuantity(s: EntryState, lineIndex: number, except?: MaterialUse): number {
    const line = s.lines[lineIndex];
    if (!line) return 0;
    return Math.max(
      0,
      this.quantityOf(line) - this.lossOnLine(s, lineIndex) - this.usedOnLine(s, lineIndex, except),
    );
  }

  // ============================================================ coûts estimés

  /** Coût rendu d'une unité reçue de chaque ligne : (achat + part des charges) / quantité utilisable. */
  landedUnitCosts(s: EntryState): (number | null)[] {
    const shares = this.chargeShares(s);
    return s.lines.map((line, i) => {
      const cost = this.lineCost(line);
      const usable = this.quantityOf(line) - this.lossOnLine(s, i);
      if (cost === null || usable <= 0) return null;
      return (cost + shares[i]) / usable;
    });
  }

  productionTotal(s: EntryState): number {
    if (!s.withTransformation) return 0;
    return s.productionCosts.reduce((sum, c) => sum + this.positive(c.amount), 0);
  }

  /** Valeur des matières utilisées, ou null si l'une d'elles n'a pas de coût connu. */
  materialsCost(s: EntryState): number | null {
    const materials = this.activeMaterials(s);
    if (!materials.length) return null;
    const landed = this.landedUnitCosts(s);
    let total = 0;
    for (const m of materials) {
      const unit = landed[m.lineIndex as number];
      if (unit === null || unit === undefined) return null;
      total += unit * this.positive(m.quantity);
    }
    return total;
  }

  /** Coût de revient estimé d'une unité d'un produit obtenu. */
  outputUnitCost(s: EntryState, o: OutputLine): number | null {
    if (!this.outputValid(s, o)) return null;
    const materials = this.materialsCost(s);
    if (materials === null) return null;
    const share = Number(o.costShare) || 0;
    return ((materials + this.productionTotal(s)) * (share / 100)) / this.positive(o.quantity);
  }

  // ====================================================================== vente

  priceKey(cardKey: string, packagingId: number | null): string {
    return `${cardKey}|${packagingId ?? 'base'}`;
  }

  /** Le prix d'une unité : celui saisi, sinon « coché » seulement pour l'unité d'origine. */
  unitPrice(sale: SalePrices, card: SaleCard, packagingId: number | null): UnitPrice {
    const stored = sale[this.priceKey(card.key, packagingId)];
    if (stored) return stored;
    return { on: (packagingId ?? null) === (card.packagingId ?? null), price: '' };
  }

  /** Les produits à vendre : ce qui reste des achats, et les produits obtenus. */
  saleCards(s: EntryState): SaleCard[] {
    const cards: SaleCard[] = [];
    const landed = this.landedUnitCosts(s);

    s.lines.forEach((line, i) => {
      const received = this.quantityOf(line);
      const remaining = this.remainingQuantity(s, i);
      if (received > 0 && remaining <= 0) return; // tout est utilisé ou perdu
      cards.push({
        key: `L${i}`,
        kind: 'purchase',
        index: i,
        product: line.product,
        packagingId: line.packagingId,
        quantity: remaining,
        cost: landed[i] ?? null,
      });
    });

    s.outputs.forEach((o, i) => {
      if (!this.outputValid(s, o)) return;
      cards.push({
        key: `O${i}`,
        kind: 'output',
        index: i,
        product: o.product,
        packagingId: o.packagingId,
        quantity: this.positive(o.quantity),
        cost: this.outputUnitCost(s, o),
      });
    });

    return cards;
  }

  /** Ventes prévues si tout est vendu, dans l'unité d'origine de chaque produit. */
  saleRevenue(s: EntryState): number {
    return this.saleCards(s).reduce((sum, card) => {
      const entry = this.unitPrice(s.sale, card, card.packagingId);
      return entry.on ? sum + this.positive(entry.price) * card.quantity : sum;
    }, 0);
  }

  // ================================================================ validation

  /** Premier problème de la fiche, avec l'onglet où le corriger, ou null si tout est bon. */
  validate(s: EntryState): { tab: number; message: string } | null {
    const incompleteCharge = this.findIncomplete(s.charges, s.lines);
    if (incompleteCharge) {
      return {
        tab: 1,
        message: `La charge « ${incompleteCharge.label || 'sans nom'} » est incomplète : choisissez un produit ou cochez « générale ».`,
      };
    }

    if (s.withLosses) {
      const bad = s.losses.find(
        (l) => l.lineIndex === null || !s.lines[l.lineIndex] || this.positive(l.quantity) <= 0,
      );
      if (bad) {
        return { tab: 2, message: 'Une perte est incomplète : choisissez le produit et la quantité perdue, ou supprimez-la.' };
      }
    }

    if (s.withTransformation) {
      if (!s.materials.length || !s.outputs.length) {
        return {
          tab: 2,
          message: 'Transformation cochée : ajoutez au moins une matière utilisée et un produit obtenu, ou décochez-la.',
        };
      }
      const badMaterial = s.materials.find(
        (m) => m.lineIndex === null || !s.lines[m.lineIndex] || this.positive(m.quantity) <= 0,
      );
      if (badMaterial) {
        return { tab: 2, message: 'Une matière utilisée est incomplète : choisissez le produit et la quantité, ou supprimez-la.' };
      }
      const seen = new Set<number>();
      for (const m of s.materials) {
        if (seen.has(m.lineIndex as number)) {
          return { tab: 2, message: 'Une même matière est utilisée deux fois : regroupez-la sur une seule ligne.' };
        }
        seen.add(m.lineIndex as number);
      }
      if (s.outputs.some((o) => this.positive(o.quantity) <= 0)) {
        return { tab: 2, message: 'Un produit obtenu n’a pas de quantité.' };
      }
      const totalShare = s.outputs.reduce((sum, o) => sum + (Number(o.costShare) || 0), 0);
      if (Math.abs(totalShare - 100) > 0.01) {
        return { tab: 2, message: `Les parts du coût des produits obtenus font ${totalShare} % : elles doivent faire 100 %.` };
      }
    }

    for (let i = 0; i < s.lines.length; i++) {
      const received = this.quantityOf(s.lines[i]);
      const out = this.lossOnLine(s, i) + this.usedOnLine(s, i);
      if (received > 0 && out > received + 1e-9) {
        return {
          tab: 2,
          message: `Pour « ${s.lines[i].product.name} », la quantité utilisée et perdue dépasse la quantité reçue.`,
        };
      }
    }
    return null;
  }

  // ============================================================ enregistrement

  /**
   * Corps de la requête unique : achat + charges + pertes + transformation + prix de vente.
   * Les indices (`lineIndex`, `index`) désignent la position dans `lines` ou `outputs`.
   */
  buildPayload(request: DeliveryRequest, s: EntryState): FullDeliveryRequest {
    const outputs = s.outputs.filter((o) => this.outputValid(s, o));
    const materials = this.activeMaterials(s);

    return {
      ...request,
      charges: this.buildCharges(s.charges, s.lines),

      losses: this.activeLosses(s).map((l) => ({
        reason: l.reason,
        lineIndex: l.lineIndex,
        quantity: String(this.positive(l.quantity)),
      })),

      transformation:
        s.withTransformation && materials.length && outputs.length
          ? {
            inputs: materials.map((m) => ({
              lineIndex: m.lineIndex,
              quantity: String(this.positive(m.quantity)),
            })),
            productionCosts: s.productionCosts
              .filter((c) => this.positive(c.amount) > 0)
              .map((c) => ({
                raison: (c.label ?? '').trim() || 'Coût de production',
                cost: this.positive(c.amount),
              })),
            outputs: s.outputs
              .map((o, index) => ({ o, index }))
              .filter(({ o }) => this.outputValid(s, o))
              .map(({ o, index }) => ({
                index,
                productId: o.product.id,
                packagingId: o.packagingId,
                quantity: String(this.positive(o.quantity)),
                costShare: Number(o.costShare) || 0,
              })),
          }
          : null,

      salePrices: this.saleCards(s)
        .map((card) => ({
          source: card.kind === 'purchase' ? ('LINE' as const) : ('OUTPUT' as const),
          index: card.index,
          productId: card.product.id,
          units: Object.entries(s.sale)
            .filter(([key, u]) => key.startsWith(card.key + '|') && u.on && this.positive(u.price) > 0)
            .map(([key, u]) => {
              const id = key.split('|')[1];
              return { packagingId: id === 'base' ? null : Number(id), price: this.positive(u.price) };
            }),
        }))
        .filter((c) => c.units.length > 0),
    };
  }

  /** Construit le corps complet puis l'envoie : c'est lui (et non l'EntryState brut) que le serveur attend. */
  registerAll(request: DeliveryRequest, s: EntryState): Observable<Supply[]> {
    return this.registerDeliveryWithCharge(this.buildPayload(request, s));
  }

  registerDeliveryWithCharge(payload: FullDeliveryRequest): Observable<Supply[]> {
    alert(JSON.stringify(payload));
    return this.http.post<Supply[]>(`${API_BASE_URL}/stock/charge-deliveries`, payload);
  }


  async openOne(): Promise<ProductDialogResult | undefined> {
    const ref = this.dialog.open<ProductDialogResult>(StockEntryDialogChargeComponent, {
      width:'860px',
      maxWidth: '95vw',
      panelClass: 'app-modal-panel',
      backdropClass: 'app-modal-backdrop',
    });
    return firstValueFrom(ref.closed);
  }
}
