import { NgTemplateOutlet } from '@angular/common';
import { Component, DoCheck, ElementRef, EventEmitter, Input, Output, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Charge, EntryResult, ExtraSale, LineResult, ProductResult, ProductSheet, ProductUnit,
  StockEntry, StockUnit,
} from './stock-entry.model';
import { StockEntryService } from './stock-entry.service';
import { Dialog, DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import {ProductDialogRequest} from '../../../core/catalog/product-rights';
/** État du petit formulaire « Nouvelle unité » : purement visuel, donc local au composant. */
interface UnitForm {
  sheet: ProductSheet;
  name: string;
  factor: number | null;
  of: string;
  error: string;
}

/**
 * Composant d'affichage : les données, les presets et les calculs viennent de
 * StockEntryService. Les getters et méthodes ci-dessous ne font que les relayer
 * au template.
 *
 * Il se comporte comme une page : il occupe toute la largeur de son conteneur,
 * sans fond de modale ni fermeture au clic extérieur. Il peut donc être routé
 * comme une page ou placé tel quel dans le corps d'une modale (celle-ci gère
 * alors l'ouverture, la fermeture et le défilement).
 */
@Component({
  selector: 'app-stock-entry',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet],
  templateUrl: './stock-entry.component.html',
  styleUrl: './stock-entry.component.scss',
})
export class StockEntryComponent implements DoCheck {
  dialogRef = inject(DialogRef<{ saved: boolean }>);
  data = inject<ProductDialogRequest>(DIALOG_DATA);



  private readonly svc = inject(StockEntryService);

  /** Mettre à false si la modale hôte affiche déjà son propre titre. */
  @Input() showHeader = true;
  /** Émis par « Annuler » : à l'hôte de fermer la modale ou de revenir en arrière. */
  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<StockEntry>();

  /** Zone qui défile : on la remet en haut à chaque changement d'onglet. */
  @ViewChild('scroller') private scroller?: ElementRef<HTMLElement>;

  /* État de l'écran */
  tab = 0;
  unitForm: UnitForm | null = null;

  /** Les champs modifient `entry` directement : on recalcule à chaque cycle. */
  ngDoCheck(): void {
    this.svc.refresh();
  }

  /* ------------------------------------------------------ données du service */

  get tabs(): string[] { return this.svc.tabs; }
  get roleLabel() { return this.svc.roleLabel; }
  get chargeModes() { return this.svc.chargeModes; }
  get saleModes() { return this.svc.saleModes; }
  get chargePresets(): Charge[] { return this.svc.chargePresets; }
  get salePresets(): Charge[] { return this.svc.salePresets; }
  get lossPresets(): string[] { return this.svc.lossPresets; }
  get costPresets(): string[] { return this.svc.costPresets; }
  get entry(): StockEntry { return this.svc.entry; }
  get result(): EntryResult { return this.svc.result; }
  get roots(): StockUnit[] { return this.svc.roots; }

  /* ----------------------------------------------------------- navigation */

  go(i: number): void {
    this.tab = i;
    this.scroller?.nativeElement.scrollTo({ top: 0 });
  }

  reset(): void {
    this.svc.reset();
    this.unitForm = null;
  }

  save(): void { this.saved.emit(this.svc.save());
    this.dialogRef.close({ saved: true });

  }

  /* ------------------------------------------------------------- relais */

  addCharge(list: Charge[], preset: Charge): void { this.svc.addCharge(list, preset); }
  addLoss(label: string): void { this.svc.addLoss(label); }
  addCost(label: string): void { this.svc.addCost(label); }
  remove<T>(list: T[], i: number): void { this.svc.remove(list, i); }
  setTransform(on: boolean): void { this.svc.setTransform(on); }
  addOutput(): void { this.svc.addOutput(); }

  unitsOf(p: ProductSheet): ProductUnit[] { return this.svc.unitsOf(p); }
  unitName(p: ProductSheet, id: string): string { return this.svc.unitName(p, id); }
  saleUnitOf(p: ProductSheet): string { return this.svc.saleUnitOf(p); }
  factorOf(id: string): number { return this.svc.factorOf(id); }
  availablePacks(p: ProductSheet): StockUnit[] { return this.svc.availablePacks(p); }
  familyUnits(p: ProductSheet): StockUnit[] { return this.svc.familyUnits(p); }
  onRefChange(p: ProductSheet): void { this.svc.onRefChange(p); }
  addPack(p: ProductSheet, id: string): void { this.svc.addPack(p, id); }
  removePack(p: ProductSheet, id: string): void { this.svc.removePack(p, id); }

  extra(p: ProductSheet, unitId: string): ExtraSale { return this.svc.extra(p, unitId); }
  proposedPrice(p: ProductSheet, unitId: string): number { return this.svc.proposedPrice(p, unitId); }
  line(r: ProductResult, unitId: string): LineResult | undefined { return this.svc.line(r, unitId); }

  fmt(v: number): string { return this.svc.fmt(v); }
  qty(v: number): string { return this.svc.qty(v); }

  /* --------------------------------------------- formulaire « Nouvelle unité » */

  openUnitForm(sheet: ProductSheet): void {
    this.unitForm = { sheet, name: '', factor: null, of: sheet.refUnit, error: '' };
  }

  confirmUnitForm(): void {
    const f = this.unitForm;
    if (!f) return;
    f.error = this.svc.createUnit(f.sheet, f.name, f.factor, f.of);
    if (!f.error) this.unitForm = null;
  }
}
