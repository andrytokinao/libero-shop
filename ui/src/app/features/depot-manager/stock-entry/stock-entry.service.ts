import { Injectable } from '@angular/core';
import {
  Charge, EntryResult, ExtraSale, LineResult, LossType, ProductResult, ProductSheet,
  ProductUnit, Role, SelectOption, StockEntry, StockUnit,
} from './stock-entry.model';

/* ==========================================================================
   DONNÉES FICTIVES (à remplacer par des appels HttpClient le moment venu)
   ========================================================================== */

const MOCK_CATALOG: StockUnit[] = [
  { id: 'u1', name: 'kg', of: null, factor: 1 },
  { id: 'u2', name: 'Sac 50 kg', of: 'u1', factor: 50 },
  { id: 'u3', name: 'Palette', of: 'u2', factor: 14 },
  { id: 'u4', name: 'Sac 25 kg', of: 'u1', factor: 25 },
  { id: 'u5', name: 'pièce', of: null, factor: 1 },
  { id: 'u6', name: 'Carton', of: 'u5', factor: 12 },
  { id: 'u7', name: 'litre', of: null, factor: 1 },
  { id: 'u8', name: 'Bidon 20 L', of: 'u7', factor: 20 },
];

const MOCK_ENTRY: StockEntry = {
  product: {
    name: 'Riz paddy', role: 'P', refUnit: 'u1', packUnits: ['u2', 'u3'],
    qty: 20, qtyUnit: 'u2', saleUnit: 'u1', price: 0, extras: {},
  },
  buyPrice: 50000,
  charges: [
    { label: 'Transport', amount: 40000, mode: 'T' },
    { label: 'Embarquement', amount: 10000, mode: 'T' },
  ],
  losses: [{ label: 'Perte de voyage', qty: 20, unit: 'u1', type: 'N' }],
  transform: true,
  method: 'V',
  productionCosts: [
    { label: 'Salaires', amount: 100000 },
    { label: 'Énergie', amount: 50000 },
  ],
  outputs: [
    {
      name: 'Riz blanc', role: 'P', refUnit: 'u1', packUnits: ['u2'], qty: 600, qtyUnit: 'u1',
      saleUnit: 'u2', price: 125000, extras: { u1: { on: true, qty: 50, price: null } },
    },
    {
      name: 'Brisures', role: 'C', refUnit: 'u1', packUnits: ['u4'], qty: 100, qtyUnit: 'u1',
      saleUnit: 'u1', price: 800, extras: {},
    },
    {
      name: 'Son', role: 'S', refUnit: 'u1', packUnits: [], qty: 150, qtyUnit: 'u1',
      saleUnit: 'u1', price: 400, extras: {},
    },
  ],
  saleCosts: [{ label: 'Transport des produits finis', amount: 30000, mode: 'T' }],
};

const blankSheet = (role: Role, refUnit = 'u1'): ProductSheet => ({
  name: '', role, refUnit, packUnits: [], qty: null, qtyUnit: refUnit,
  saleUnit: refUnit, price: null, extras: {},
});

const n = (v: unknown): number => parseFloat(String(v ?? '')) || 0;

/* ==========================================================================
   SERVICE
   Toute la donnée et tout le calcul de la fiche vivent ici. Le composant ne
   garde que l'état de l'écran (onglet ouvert, formulaire d'unité).
   `providedIn: 'root'` : le brouillon survit à la fermeture de la fiche. Pour une
   fiche neuve à chaque ouverture, déclarez-le plutôt dans `providers` du composant.
   ========================================================================== */

@Injectable({ providedIn: 'root' })
export class StockEntryService {
  /* ------------------------------------------------------------ référentiels */

  readonly tabs = ['Achat', 'Charges', 'Pertes et transformation', 'Vente'];

  readonly roleLabel: Record<Role, string> = {
    P: 'Produit principal', C: 'Produit secondaire', S: 'Sous-produit',
  };

  readonly chargeModes: SelectOption[] = [
    { v: 'T', l: 'Pour tout le lot' },
    { v: 'U', l: 'Par unité d’achat' },
  ];

  readonly saleModes: SelectOption[] = [
    { v: 'T', l: 'Pour tout le lot vendu' },
    { v: 'U', l: 'Par unité vendue' },
  ];

  readonly chargePresets: Charge[] = [
    { label: 'Transport', amount: null, mode: 'T' },
    { label: 'Embarquement', amount: null, mode: 'T' },
    { label: 'Débarquement', amount: null, mode: 'T' },
    { label: 'Autre frais', amount: null, mode: 'T' },
  ];

  readonly salePresets: Charge[] = [
    { label: 'Transport des produits finis', amount: null, mode: 'T' },
    { label: 'Commission', amount: null, mode: 'T' },
    { label: 'Emballage de vente', amount: null, mode: 'U' },
    { label: 'Autre frais', amount: null, mode: 'T' },
  ];

  readonly lossPresets = ['Perte de voyage', 'Produit périmé ou abîmé', 'Vol ou accident'];
  readonly costPresets = ['Salaires', 'Énergie', 'Emballage', 'Autre coût'];

  /* ------------------------------------------------------------------- état */

  catalog: StockUnit[] = structuredClone(MOCK_CATALOG);
  entry: StockEntry = structuredClone(MOCK_ENTRY);
  /** Entrées enregistrées pendant la session (simule une persistance). */
  history: StockEntry[] = [];
  result: EntryResult = this.compute();

  /** À appeler quand la saisie change : met le résultat à jour. */
  refresh(): void {
    this.result = this.compute();
  }

  /* ---------------------------------------------------------------- actions */

  reset(): void {
    this.entry = {
      product: blankSheet('P'), buyPrice: null, charges: [], losses: [], transform: false,
      method: 'V', productionCosts: [], outputs: [], saleCosts: [],
    };
    this.refresh();
  }

  /** Enregistre une copie de l'entrée et la renvoie. */
  save(): StockEntry {
    const copy = structuredClone(this.entry);
    this.history.push(copy);
    return copy;
  }

  addCharge(list: Charge[], preset: Charge): void {
    list.push({ ...preset });
  }

  addLoss(label: string): void {
    const type: LossType = label === 'Perte de voyage' ? 'N' : 'A';
    this.entry.losses.push({ label, qty: null, unit: this.entry.product.refUnit, type });
  }

  addCost(label: string): void {
    this.entry.productionCosts.push({ label, amount: null });
  }

  remove<T>(list: T[], i: number): void {
    list.splice(i, 1);
  }

  setTransform(on: boolean): void {
    this.entry.transform = on;
    if (on && !this.entry.outputs.length) {
      const s = blankSheet('P', this.entry.product.refUnit);
      s.name = this.entry.product.name;
      this.entry.outputs.push(s);
    }
  }

  addOutput(): void {
    this.entry.outputs.push(blankSheet('C', this.entry.product.refUnit));
  }

  /* ----------------------------------------------------------------- unités */

  get roots(): StockUnit[] {
    return this.catalog.filter((u) => !u.of);
  }

  unit(id: string): StockUnit | undefined {
    return this.catalog.find((u) => u.id === id);
  }

  rootOf(id: string, d = 0): string {
    const u = this.unit(id);
    if (!u || d > 20) return id;
    return u.of ? this.rootOf(u.of, d + 1) : id;
  }

  /** Combien d'unités de référence contient cette unité. */
  factorOf(id: string, d = 0): number {
    const u = this.unit(id);
    if (!u || d > 20) return 1;
    return u.of ? (n(u.factor) || 1) * this.factorOf(u.of, d + 1) : 1;
  }

  /** Unités d'un produit : la référence en premier, puis ses conditionnements. */
  unitsOf(p: ProductSheet): ProductUnit[] {
    const list: ProductUnit[] = [{ id: p.refUnit, name: this.unit(p.refUnit)?.name ?? 'unité', factor: 1 }];
    p.packUnits.forEach((id) => {
      const u = this.unit(id);
      if (u && id !== p.refUnit && this.rootOf(id) === p.refUnit) {
        list.push({ id, name: u.name, factor: this.factorOf(id) });
      }
    });
    return list;
  }

  factor(p: ProductSheet, id: string): number {
    return this.unitsOf(p).find((u) => u.id === id)?.factor ?? 1;
  }

  unitName(p: ProductSheet, id: string): string {
    return (this.unitsOf(p).find((u) => u.id === id) ?? this.unitsOf(p)[0]).name;
  }

  saleUnitOf(p: ProductSheet): string {
    return this.unitsOf(p).some((u) => u.id === p.saleUnit) ? p.saleUnit : p.refUnit;
  }

  /** Les conditionnements du catalogue encore disponibles pour ce produit. */
  availablePacks(p: ProductSheet): StockUnit[] {
    const used = this.unitsOf(p).map((u) => u.id);
    return this.catalog.filter((u) => this.rootOf(u.id) === p.refUnit && !used.includes(u.id));
  }

  /** Les unités dans lesquelles une nouvelle unité peut se mesurer. */
  familyUnits(p: ProductSheet): StockUnit[] {
    return this.catalog.filter((u) => this.rootOf(u.id) === p.refUnit);
  }

  /** Changer l'unité de référence repart de zéro pour ce produit. */
  onRefChange(p: ProductSheet): void {
    p.packUnits = [];
    p.saleUnit = p.qtyUnit = p.refUnit;
    p.extras = {};
    if (p === this.entry.product) this.entry.losses.forEach((l) => (l.unit = p.refUnit));
  }

  addPack(p: ProductSheet, id: string): void {
    if (id) p.packUnits = [...p.packUnits, id];
  }

  removePack(p: ProductSheet, id: string): void {
    p.packUnits = p.packUnits.filter((x) => x !== id);
    delete p.extras[id];
    if (p.saleUnit === id) p.saleUnit = p.refUnit;
    if (p.qtyUnit === id) p.qtyUnit = p.refUnit;
    if (p === this.entry.product) {
      this.entry.losses.forEach((l) => l.unit === id && (l.unit = p.refUnit));
    }
  }

  /** Crée une unité et l'ajoute au produit. Renvoie un message d'erreur, ou '' si tout va bien. */
  createUnit(p: ProductSheet, name: string, factor: number | null, of: string): string {
    if (!name.trim()) return 'Donnez un nom à l’unité.';
    if (!(n(factor) > 0)) return 'Indiquez combien elle contient.';
    const id = `u${this.catalog.length + 1}_${Date.now()}`;
    this.catalog.push({ id, name: name.trim(), of, factor: n(factor) });
    p.packUnits = [...p.packUnits, id];
    return '';
  }

  /* ------------------------------------------------------- vente par unité */

  extra(p: ProductSheet, unitId: string): ExtraSale {
    return (p.extras[unitId] ??= { on: false, qty: null, price: null });
  }

  proposedPrice(p: ProductSheet, unitId: string): number {
    return (n(p.price) / this.factor(p, this.saleUnitOf(p))) * this.factor(p, unitId);
  }

  line(r: ProductResult, unitId: string): LineResult | undefined {
    return r.lines.find((l) => l.unitId === unitId);
  }

  /* --------------------------------------------------------------- formats */

  fmt(v: number): string {
    return (Math.round(v * 100) / 100).toLocaleString('fr-FR') + ' Ar';
  }

  qty(v: number): string {
    return (Math.round(v * 1000) / 1000).toLocaleString('fr-FR');
  }

  /* ---------------------------------------------------------------- calcul */

  private compute(): EntryResult {
    const e = this.entry;
    const m = e.product;
    const alerts: string[] = [];

    const qb = n(m.qty);
    const q = qb * this.factor(m, m.qtyUnit);
    const buy = qb * n(e.buyPrice);
    const charges = e.charges.reduce((s, c) => s + (c.mode === 'T' ? n(c.amount) : n(c.amount) * qb), 0);

    let lossN = 0;
    let lossA = 0;
    e.losses.forEach((l) => {
      const x = n(l.qty) * this.factor(m, l.unit);
      if (l.type === 'N') lossN += x; else lossA += x;
    });

    const prod = e.productionCosts.reduce((s, c) => s + n(c.amount), 0);
    const cost = buy + charges;
    const net = q - lossN;
    const unitCost = net > 0 ? cost / net : 0;
    const sellable = q - lossN - lossA;
    const rn = this.unitName(m, m.refUnit);

    if (lossA > 0) {
      alerts.push(`Perte anormale : ${this.qty(lossA)} ${rn}, soit ${this.fmt(lossA * unitCost)}, déjà déduite du bénéfice.`);
    }
    if (sellable < 0) alerts.push('Les pertes dépassent la quantité achetée. Vérifiez les chiffres.');

    const total = Math.max(sellable, 0) * unitCost + (e.transform ? prod : 0);

    /* Les produits à vendre : les produits obtenus, ou le produit acheté tel quel. */
    const items = e.transform
      ? e.outputs.map((o) => ({ s: o, role: o.role, qr: n(o.qty) * this.factor(o, o.qtyUnit) }))
      : [{ s: m, role: 'P' as Role, qr: Math.max(sellable, 0) }];

    interface Work {
      s: ProductSheet; role: Role; qr: number; pu: string; fs: number; sq: number; nu: number;
      r: number; over: boolean; prices: Record<string, number>; c: number; sc: number; pf: number;
    }
    const W: Work[] = items.map((it) => {
      const s = it.s;
      const pu = this.saleUnitOf(s);
      const fs = this.factor(s, pu);
      let exq = 0, exr = 0, nu = 0;
      const prices: Record<string, number> = {};
      this.unitsOf(s).forEach((u) => {
        if (u.id === pu) return;
        const ex = s.extras[u.id];
        if (ex?.on) {
          const qq = n(ex.qty);
          const pp = ex.price != null ? n(ex.price) : (n(s.price) / fs) * u.factor;
          exq += qq * u.factor;
          exr += qq * pp;
          nu += qq;
          prices[u.id] = pp;
        }
      });
      const sq = Math.max(it.qr - exq, 0) / fs;
      return {
        s, role: it.role, qr: it.qr, pu, fs, sq, nu: nu + sq, r: sq * n(s.price) + exr,
        over: it.qr - exq < -1e-9, prices, c: 0, sc: 0, pf: 0,
      };
    });

    const rev = W.reduce((a, w) => a + w.r, 0);
    const sumQ = W.reduce((a, w) => a + w.qr, 0);
    const sec = W.filter((w) => w.role !== 'P').reduce((a, w) => a + w.r, 0);
    const pq = W.filter((w) => w.role === 'P').reduce((a, w) => a + w.qr, 0);

    if (e.transform && e.outputs.every((o) => o.refUnit === m.refUnit)) {
      const lost = sellable - sumQ;
      if (lost < -0.0001) {
        alerts.push(`Vous avez obtenu ${this.qty(-lost)} ${rn} de plus que la quantité mise en production. Vérifiez les quantités.`);
      } else if (lost > 0.0001) {
        alerts.push(`Perte pendant la transformation : ${this.qty(lost)} ${rn} (calculée automatiquement, son coût est réparti sur les produits obtenus).`);
      }
    }

    let saleCostTotal = 0;
    let gain = 0;
    const products: ProductResult[] = W.map((w) => {
      const share = rev > 0 ? w.r / rev : sumQ > 0 ? w.qr / sumQ : 0;
      if (!e.transform) w.c = total;
      else if (e.method === 'V') w.c = total * share;
      else w.c = w.role === 'P' ? (total - sec) * (pq > 0 ? w.qr / pq : 0) : 0;

      w.sc = e.saleCosts.reduce((s, c) => s + (c.mode === 'T' ? n(c.amount) * share : n(c.amount) * w.nu), 0);
      w.pf = w.r - w.c - w.sc;
      saleCostTotal += w.sc;
      gain += w.pf;

      const costPerRef = w.qr > 0 ? (w.c + w.sc) / w.qr : 0;
      const lines: LineResult[] = this.unitsOf(w.s).map((u) => {
        const isMain = u.id === w.pu;
        const price = isMain ? n(w.s.price) : (w.prices[u.id] ?? 0);
        const minPrice = costPerRef * u.factor;
        return {
          unitId: u.id, unitName: u.name, isMain, remaining: isMain ? w.sq : 0,
          minPrice, price, margin: price > 0 ? price - minPrice : 0,
        };
      });

      return {
        sheet: w.s,
        name: w.s.name || (e.transform ? 'Produit obtenu' : 'Produit'),
        role: w.role,
        stockLabel: `${this.qty(w.qr)} ${this.unitName(w.s, w.s.refUnit)}` +
          (w.fs !== 1 ? ` soit ${this.qty(w.qr / w.fs)} ${this.unitName(w.s, w.pu)}` : ''),
        over: w.over,
        lines,
        profit: w.pf,
      };
    });

    return {
      totalCharges: charges,
      totalCost: cost + (e.transform ? prod : 0) + saleCostTotal,
      revenue: rev,
      profit: gain - lossA * unitCost,
      saleCostTotal,
      alerts,
      products,
    };
  }
}
