import {
  Component, ElementRef, Input, OnInit, ViewChild, computed, inject, input, output, signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { apiResource } from '../../../core/api/api-resource';
import { CatalogApi } from '../../../core/api/catalog.api';
import { CostingApi } from '../../../core/api/costing.api';
import { ProductCost, Supplier } from '../../../core/models';
import { formatAmount, unitChoiceLabel, unitsOf } from '../../../core/sale/sale-unit';
import { StockUndo } from '../../../core/stock/stock-undo';
import { HelpButtonComponent } from '../help-button.component';
import {
  ChargeLine,
  EntryState,
  LossEntry,
  LossReasonCode,
  MaterialUse,
  OutputLine,
  ProductionCostLine,
  SaleCard,
  SalePrices,
  SypplyService,
} from '../../../features/depot-manager/stock-entry/sypply.service';
import { ToastService } from '../../../core/services/toast.service';
import { ProductStore } from '../../../core/store/product.store';
import {
  addToNote,
  DeliveryLine,
  deliveryRequest,
  lastCostText,
  lineTotal,
  noteTotal,
  unitOf,
} from '../../../core/stock/delivery-note';
import { CodeMatch } from '../../../core/catalog/product-pick';
import { isTopmostDialog } from '../../../core/ui/topmost-dialog';
import { AriaryPipe } from '../../pipes/ariary.pipe';
import { ProductPickerComponent } from '../product-picker.component';

/**
 * Ajoute du stock : achat, charges, pertes et transformation, prix de vente.
 * Tout est enregistré en une seule fois avec le bouton final.
 */
@Component({
  selector: 'app-stock-entry-dialog',
  standalone: true,
  imports: [HelpButtonComponent, FormsModule, NgTemplateOutlet, AriaryPipe, ProductPickerComponent],
  host: { '(document:keydown.escape)': 'close()' },
  templateUrl: 'stock-entry-dialog-charge.component.html',
  styleUrl: 'stock-entry-dialog-charge.component.scss',
})
export class StockEntryDialogChargeComponent implements OnInit {
  private readonly svc = inject(SypplyService);
  private readonly catalog = inject(CatalogApi);
  private readonly costing = inject(CostingApi);
  private readonly undo = inject(StockUndo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly toasts = inject(ToastService);
  protected readonly unitChoiceLabel = unitChoiceLabel;
  protected readonly unitsOf = unitsOf;
  protected readonly unitOf = unitOf;
  protected readonly lineTotal = lineTotal;

  /** "Nouvelle réception" depuis le menu d'un fournisseur : le bon démarre avec lui. */
  readonly initialSupplierId = input<number | null>(null);
  readonly changed = output<void>();
  readonly closed = output<void>();
  @Input() showHeader = true;

  protected readonly products = inject(ProductStore).list().value;
  private readonly supplierResource = apiResource<Supplier[]>([], () => this.catalog.suppliers());
  protected readonly suppliers = computed(() =>
    this.supplierResource.value().filter((s) => !s.retired || s.id === this.supplierId()),
  );
  private readonly costs = apiResource<ProductCost[]>([], () => this.costing.productCosts()).value;

  protected readonly supplierId = signal<number | null>(null);
  protected readonly lines = signal<DeliveryLine[]>([]);
  protected readonly busy = signal(false);
  protected readonly creatingSupplier = signal(false);
  protected readonly total = computed(() => noteTotal(this.lines()));
  protected readonly request = computed(() => deliveryRequest(this.supplierId(), this.lines()));

  // ------------------------------------------------------------ état des onglets
  charges: ChargeLine[] = [];

  /** Onglet 3 : tout est décoché par défaut. */
  withLosses = false;
  losses: LossEntry[] = [];
  withTransformation = false;
  materials: MaterialUse[] = [];
  outputs: OutputLine[] = [];
  productionCosts: ProductionCostLine[] = [];

  /** Onglet 4 : prix de vente par produit et par unité. */
  sale: SalePrices = {};

  readonly chargePresets = this.svc.chargePresets;
  readonly chargeBases = this.svc.chargeBases;
  readonly productionCostPresets = this.svc.productionCostPresets;

  tab = 0;
  fin = false;
  @ViewChild('scroller') private scroller?: ElementRef<HTMLElement>;

  ngOnInit(): void {
    this.supplierId.set(this.initialSupplierId());
  }

  get tabs(): string[] {
    return this.svc.tabs;
  }

  /** Tout ce qui est saisi, pour le service. */
  get state(): EntryState {
    return {
      lines: this.lines(),
      charges: this.charges,
      withLosses: this.withLosses,
      losses: this.losses,
      withTransformation: this.withTransformation,
      materials: this.materials,
      outputs: this.outputs,
      productionCosts: this.productionCosts,
      sale: this.sale,
    };
  }

  // ------------------------------------------------------------ Achat
  protected add(match: CodeMatch): void {
    this.lines.update((lines) => addToNote(lines, match.product, match.packagingId, this.costOf(match.product.id)));
  }

  protected setUnit(index: number, packagingId: number | null): void {
    const line = this.lines()[index];
    this.patch(index, { packagingId, unitCost: lastCostText(this.costOf(line.product.id), packagingId) });
  }

  protected patch(index: number, change: Partial<DeliveryLine>): void {
    this.lines.update((lines) => lines.map((line, i) => (i === index ? { ...line, ...change } : line)));
  }

  protected remove(index: number): void {
    this.lines.update((lines) => lines.filter((_, i) => i !== index));
    this.charges = this.svc.onLineRemoved(this.charges, index);
    this.materials = this.svc.reindex(this.materials, index);
    this.losses = this.svc.reindex(this.losses, index);
    this.sale = this.svc.shiftSale(this.sale, 'L', index);
  }

  protected onSupplierCreated(supplier: Supplier): void {
    this.supplierResource.reload();
    this.supplierId.set(supplier.id);
  }

  // ------------------------------------------------------------ Charges
  addCharge(preset: { label: string }): void {
    this.charges = [...this.charges, this.svc.newCharge(preset.label)];
  }

  removeCharge(index: number): void {
    this.charges = this.charges.filter((_, i) => i !== index);
  }

  onGeneralChange(c: ChargeLine, general: boolean): void {
    c.general = general;
    if (!general && c.lineIndex === null && this.lines().length) {
      c.lineIndex = 0;
    }
  }

  chargeTotal(c: ChargeLine): number {
    return this.svc.chargeTotal(c, this.lines());
  }

  lineLabel(line: DeliveryLine): string {
    return `${line.product.name} — ${this.unitOf(line).label ?? 'unité'}`;
  }

  lineUnitLabel(index: number | null): string {
    const line = index === null ? undefined : this.lines()[index];
    return line ? (this.unitOf(line).label ?? 'unité') : '';
  }

  // ------------------------------------------------------------ Pertes
  onWithLosses(checked: boolean): void {
    this.withLosses = checked;
    if (checked && !this.losses.length) {
      this.addLoss();
    }
  }

  /** Les motifs proposés : « fabrication » seulement quand il y a une transformation. */
  get lossReasonOptions(): { v: LossReasonCode; l: string }[] {
    return this.svc.lossReasons.filter((r) => this.withTransformation || r.v !== 'PROCESS');
  }

  addLoss(): void {
    this.losses = [
      ...this.losses,
      { reason: 'TRANSPORT', lineIndex: this.lines().length ? 0 : null, quantity: '' },
    ];
  }

  removeLoss(index: number): void {
    this.losses = this.losses.filter((_, i) => i !== index);
  }

  // ------------------------------------------------------------ Transformation
  onWithTransformation(checked: boolean): void {
    this.withTransformation = checked;
    if (checked && !this.materials.length && this.lines().length) {
      this.addMaterial();
    }
    if (!checked) {
      // Une perte de fabrication n'a plus de sens sans transformation.
      this.losses = this.losses.map((l) => (l.reason === 'PROCESS' ? { ...l, reason: 'OTHER' as const } : l));
    }
  }

  private defaultUsed(lineIndex: number, except?: MaterialUse): string {
    const q = this.svc.remainingQuantity(this.state, lineIndex, except);
    return q > 0 ? String(Math.round(q * 1000) / 1000) : '';
  }

  addMaterial(): void {
    const taken = new Set(this.materials.map((m) => m.lineIndex));
    const free = this.lines().findIndex((_, i) => !taken.has(i));
    const lineIndex = free >= 0 ? free : null;
    this.materials = [
      ...this.materials,
      { lineIndex, quantity: lineIndex === null ? '' : this.defaultUsed(lineIndex) },
    ];
  }

  onMaterialLine(m: MaterialUse, lineIndex: number | null): void {
    m.lineIndex = lineIndex;
    m.quantity = lineIndex === null ? '' : this.defaultUsed(lineIndex, m);
  }

  removeMaterial(index: number): void {
    this.materials = this.materials.filter((_, i) => i !== index);
  }

  addOutput(match: CodeMatch): void {
    this.outputs = [
      ...this.outputs,
      { product: match.product, packagingId: match.packagingId, quantity: '', costShare: null },
    ];
    this.rebalanceShares();
  }

  removeOutput(index: number): void {
    this.outputs = this.outputs.filter((_, i) => i !== index);
    this.sale = this.svc.shiftSale(this.sale, 'O', index);
    this.rebalanceShares();
  }

  /** Parts égales entre les produits obtenus ; le dernier prend l'arrondi pour faire 100 %. */
  rebalanceShares(): void {
    const n = this.outputs.length;
    if (!n) return;
    const base = Math.floor(10000 / n) / 100;
    this.outputs.forEach((o) => (o.costShare = base));
    const last = Math.round((100 - base * (n - 1)) * 100) / 100;
    this.outputs[n - 1].costShare = last;
  }

  sharesTotal(): number {
    return Math.round(this.outputs.reduce((sum, o) => sum + (Number(o.costShare) || 0), 0) * 100) / 100;
  }

  sharesOk(): boolean {
    return Math.abs(this.sharesTotal() - 100) <= 0.01;
  }

  outputCost(o: OutputLine): number | null {
    return this.svc.outputUnitCost(this.state, o);
  }

  addProductionCost(preset: { label: string }): void {
    this.productionCosts = [...this.productionCosts, { label: preset.label, amount: null }];
  }

  removeProductionCost(index: number): void {
    this.productionCosts = this.productionCosts.filter((_, i) => i !== index);
  }

  // ------------------------------------------------------------ Vente
  cards(): SaleCard[] {
    return this.svc.saleCards(this.state);
  }

  cardUnitLabel(card: SaleCard): string {
    const unit = unitsOf(card.product).find((u) => u.packagingId === card.packagingId);
    return unit ? unitChoiceLabel(unit) : '';
  }

  unitOn(card: SaleCard, unit: { packagingId: number | null }): boolean {
    return this.svc.unitPrice(this.sale, card, unit.packagingId).on;
  }

  unitPriceText(card: SaleCard, unit: { packagingId: number | null }): string {
    return this.svc.unitPrice(this.sale, card, unit.packagingId).price;
  }

  setUnitOn(card: SaleCard, unit: { packagingId: number | null }, on: boolean): void {
    const current = this.svc.unitPrice(this.sale, card, unit.packagingId);
    this.sale = { ...this.sale, [this.svc.priceKey(card.key, unit.packagingId)]: { ...current, on } };
  }

  /** Saisir un prix coche l'unité : on ne tape pas un prix pour une unité qu'on ne vend pas. */
  setUnitPrice(card: SaleCard, unit: { packagingId: number | null }, price: string): void {
    const current = this.svc.unitPrice(this.sale, card, unit.packagingId);
    this.sale = {
      ...this.sale,
      [this.svc.priceKey(card.key, unit.packagingId)]: { on: current.on || price.trim() !== '', price },
    };
  }

  /** Marge sur l'unité d'origine : prix − coût de revient estimé. */
  margin(card: SaleCard, unit: { packagingId: number | null }): number | null {
    if ((unit.packagingId ?? null) !== (card.packagingId ?? null) || card.cost === null) return null;
    const entry = this.svc.unitPrice(this.sale, card, unit.packagingId);
    const price = this.svc.positive(entry.price);
    return entry.on && price > 0 ? price - card.cost : null;
  }

  // ------------------------------------------------------------ résumé
  get result() {
    const totalCharges = this.svc.sumCharges(this.charges, this.lines());
    const purchase = this.total().amount ?? 0;
    const production = this.svc.productionTotal(this.state);
    const totalCost = purchase + totalCharges + production;
    const revenue = this.svc.saleRevenue(this.state);
    return { totalCharges, production, totalCost, revenue, profit: revenue - totalCost };
  }

  fmt(value: number): string {
    return `${formatAmount(Math.round(value), null)} Ar`;
  }

  // ------------------------------------------------------------ navigation
  go(i: number): void {
    this.tab = i;
    this.scroller?.nativeElement.scrollTo({ top: 0 });
  }

  get isLast(): boolean {
    return this.tab >= this.tabs.length - 1;
  }

  protected next(): void {
    if (!this.isLast) {
      this.go(this.tab + 1);
      return;
    }
    this.fin = true;
    this.save();
  }

  // ------------------------------------------------------------ enregistrement unique
  protected save(): void {
    const request = this.request();
    if (!request || this.busy()) return;

    const problem = this.svc.validate(this.state);
    if (problem) {
      this.toasts.show(problem.message);
      this.go(problem.tab);
      return;
    }

    this.busy.set(true);
    this.svc.registerAll(request, this.state).subscribe({
      next: (supplies) => {
        const amount = this.result.totalCost;
        this.undo.received(
          `Réception enregistrée : ${supplies.length} produit(s)` +
          (amount ? `, coût total ${formatAmount(Math.round(amount), null)} Ar.` : '.'),
          supplies,
        );
        this.changed.emit();
        this.closed.emit();
      },
      // Rien n'a été enregistré : le toast d'erreur dit pourquoi, la saisie reste.
      error: () => this.busy.set(false),
    });
  }

  private costOf(productId: number): ProductCost | undefined {
    return this.costs().find((cost) => cost.productId === productId);
  }

  protected close(): void {
    if (isTopmostDialog(this.host.nativeElement)) {
      this.closed.emit();
    }
  }
}
