import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { UnitModelApi } from '../../core/api/unit-model.api';
import { parseDecimal } from '../../core/catalog/decimal-text';
import { fold } from '../../core/catalog/unit-model-plan';
import { ConfigTab, ProductRights } from '../../core/catalog/product-rights';
import {
  CategoryNode,
  Packaging,
  PackagingRequest,
  Product,
  ProductCost,
  ProductUnits,
  UnitCost,
  UnitModel,
  UpdateProductRequest,
} from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { BarcodesEditorComponent } from './barcodes-editor.component';
import { CameraScanButtonComponent } from './camera-scan-button.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { FilterTab, FilterTabsComponent } from './filter-tabs.component';
import { CategoryPickerComponent } from './category-picker.component';
import { UnitModelPickerComponent } from './unit-model-picker.component';
import { UnitModelsDialogComponent } from './unit-models-dialog.component';
import { HelpButtonComponent } from './help-button.component';

/** The add/edit form, as typed. Strings because an empty field is not zero. */
interface Draft {
  label: string;
  factor: string;
  /** The unit the contenance is counted in, by name; null for the base unit. */
  reference: string | null;
  price: string;
  barcode: string;
}

const EMPTY_DRAFT: Draft = { label: '', factor: '', reference: null, price: '', barcode: '' };

/** The product's own sheet, as typed. */
interface Sheet {
  name: string;
  price: string;
  barcode: string;
  categoryId: number | null;
  /** Purchase cost of one purchase unit; empty while none is known. */
  cost: string;
  /** The unit it is bought in; null for the base unit. */
  purchaseUnitId: number | null;
}

/** « Ajouter aussi au modèle » under the unit form. */
interface Keep {
  on: boolean;
  /** The model to add the unit to; null for a new one. */
  modelId: number | null;
  /** The new model's name; empty for the one proposed. */
  name: string;
}

const KEEP_OFF: Keep = { on: false, modelId: null, name: '' };

const EMPTY_SHEET: Sheet = {
  name: '',
  price: '',
  barcode: '',
  categoryId: null,
  cost: '',
  purchaseUnitId: null,
};

/**
 * A product's configuration: its sheet — name, rayon, barcode, sale price, purchase price — and
 * the units it is sold and bought in: its base unit, and "1 kg = 3.5 kapoka"-style packagings,
 * each with its sale price, its cost and the profit left on it. Everything about it but its
 * stock, which moves only by a receipt, a sale or a correction — the stock manager's "Ajouter du
 * stock" hands over to the receipt dialog.
 *
 * <p>The purchase price is typed in the unit the shop buys in — a tonne — and never for the base
 * unit: the server keeps it per base unit and prices every other unit from it.
 *
 * <p>Talks to the server itself rather than through its parent, unlike the import dialogs: it
 * edits one product's units and nothing else, every write answers the whole set of units, and the
 * parent has nothing to decide in between. The parent only hears that something changed, so it can
 * refresh the stock list when the base unit's name moved.
 *
 * <p>The factor is written the way a shopkeeper says it — "1 kg contient 3,5 kapoka" — with the
 * base unit's name beside the box, never as a bare "facteur". A comma is accepted as well as a
 * point: it is the decimal separator on every keyboard here.
 */
/** The packagings a shop sells in most: a tap names the unit being added. */
const UNIT_CHIPS: readonly string[] = ['carton', 'sac', 'paquet', 'douzaine', 'kg', 'litre'];

@Component({
  selector: 'app-product-units-dialog',
  standalone: true,
  imports: [
    HelpButtonComponent,
    FormsModule,
    NgTemplateOutlet,
    AriaryPipe,
    CameraScanButtonComponent,
    CategoryPickerComponent,
    UnitModelPickerComponent,
    UnitModelsDialogComponent,
    FilterTabsComponent,
    ConfirmDialogComponent,
    BarcodesEditorComponent,
  ],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop cfg-backdrop" (click)="onBackdrop($event)">
      <div class="modal cfg" role="dialog" aria-modal="true" aria-labelledby="units-title">
        <div class="modal-head">
          <div class="cfg-title">
            <h2 id="units-title">Configuration — {{ units()?.productName ?? productName }}</h2>
            <details class="cfg-help muted">
              <summary>Comment ça marche ?</summary>
              Le stock est compté dans l'unité de base, la plus petite. Chaque autre unité s'écrit
              comme sur l'emballage (« sac 25 kg » contient 25 kg). Le prix d'entrée se saisit dans
              l'unité d'achat — la tonne, le sac — et le calcul par unité de base est fait pour vous.
              Les unités courantes (g, kg, tonne…) se cochent depuis un modèle.
            </details>
          </div>
          <app-help topic="product-config" />
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (units(); as u) {
            <!-- Four tabs over one sheet: the price depends on the units, so adding a tonne and
                 typing its price is done without closing anything. -->
            <app-filter-tabs class="cfg-tabs" label="Parties de la configuration" [tabs]="tabs" [wrap]="true" [(value)]="tab" />

            <!-- ------------------------------------------------- the sheet -->
            <!-- A past ticket keeps the name and price it was sold at: correcting them here only
                 changes what the next sale reads. -->
            <!-- Nothing of the sheet is in the units and codes tabs; its "Enregistrer" still is, if a change waits. -->
            <form class="un-sheet" [class.un-off]="(tab() === 'unites' || tab() === 'codes') && !sheetChanged()" (ngSubmit)="saveSheet()">
              <div class="fld un-sheet-name" [class.un-off]="tab() !== 'fiche'">
                <label for="un-name">Nom du produit</label>
                <input
                  id="un-name"
                  name="name"
                  type="text"
                  autocomplete="off"
                  maxlength="255"
                  [ngModel]="sheet().name"
                  (ngModelChange)="patchSheet({ name: $event })"
                />
              </div>
              <div class="fld" [class.un-off]="tab() !== 'fiche'">
                <label for="un-category">Catégorie</label>
                <app-category-picker
                  fieldId="un-category"
                  noneLabel="Sans catégorie"
                  [categories]="categories()"
                  [value]="sheet().categoryId"
                  (valueChange)="patchSheet({ categoryId: $event })"
                />
              </div>
              <!-- Kept in the sheet so a save sends it unchanged; edited in the codes tab, line by line. -->
              <div class="fld un-off">
                <label for="un-base-barcode">Code-barres</label>
                <!-- Typed, read by a USB scanner (which types it and presses Entrée), or read by
                     the phone camera. Entrée stays in the field: a scan must not save the sheet. -->
                <div class="un-suffixed">
                  <input
                    id="un-base-barcode"
                    name="barcode"
                    type="text"
                    autocomplete="off"
                    maxlength="64"
                    placeholder="scanner ou saisir"
                    [ngModel]="sheet().barcode"
                    (ngModelChange)="patchSheet({ barcode: $event })"
                    (keydown.enter)="$event.preventDefault()"
                  />
                  <app-camera-scan-button (scanned)="patchSheet({ barcode: $event })" />
                </div>
              </div>
              <div class="fld" [class.un-off]="tab() !== 'prix'">
                <label for="un-base-price">Prix de vente (Ar) par {{ baseName() }}</label>
                <input
                  id="un-base-price"
                  name="price"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  [ngModel]="sheet().price"
                  (ngModelChange)="patchSheet({ price: $event })"
                />
              </div>
              <!-- What the shop pays, said in the unit it buys in: a tonne, not a kapoka. The
                   server keeps it per base unit and works out every other unit from it. -->
              <div class="fld un-cost" [class.un-off]="tab() !== 'prix'">
                <label for="un-cost">Prix d'entrée (Ar)</label>
                <div class="un-suffixed">
                  <input
                    id="un-cost"
                    name="cost"
                    type="text"
                    inputmode="decimal"
                    autocomplete="off"
                    placeholder="inconnu"
                    [ngModel]="sheet().cost"
                    (ngModelChange)="patchSheet({ cost: $event })"
                  />
                  <select
                    name="purchaseUnit"
                    class="field"
                    aria-label="Unité d'achat"
                    [ngModel]="sheet().purchaseUnitId"
                    (ngModelChange)="pickPurchaseUnit($event)"
                  >
                    <option [ngValue]="null">par {{ baseName() }}</option>
                    @for (p of u.packagings; track p.id) {
                      <option [ngValue]="p.id">par {{ p.label }}</option>
                    }
                  </select>
                </div>
              </div>
              <div class="un-cost-hint muted" [class.un-off]="tab() !== 'prix'">
                @if (costHint(); as hint) {
                  {{ hint }}
                } @else if (u.packagings.length === 0) {
                  Acheté dans une autre unité (tonne, sac…) ? Ajoutez-la ci-dessous, puis
                  choisissez-la ici.
                }
              </div>
              @if (sheetChanged()) {
                <div class="un-sheet-foot">
                  <button class="btn small" type="submit" [disabled]="busy() || !sheetRequest()">
                    Enregistrer
                  </button>
                </div>
              }
            </form>

            <!-- Its own alert threshold, saved apart from the sheet: a change here sets nothing else. -->
            <div class="un-alert" [class.un-off]="tab() !== 'fiche'">
              <div class="fld">
                <label for="un-alert">Seuil d'alerte ({{ baseName() }})</label>
                <input
                  id="un-alert"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  [placeholder]="formatFactor(u.defaultLowStockThreshold) + ' (seuil commun)'"
                  [ngModel]="alertDraft()"
                  (ngModelChange)="alertDraft.set($event)"
                  (keydown.enter)="saveAlert()"
                />
              </div>
              @if (alertChanged()) {
                <button class="btn small" type="button" [disabled]="busy() || !alertValid()" (click)="saveAlert()">
                  Enregistrer le seuil
                </button>
              }
              <div class="un-base-facts muted">
                En alerte sous {{ formatFactor(u.lowStockThreshold ?? u.defaultLowStockThreshold) }} {{ baseName() }}
                @if (u.lowStockThreshold === null) {
                  (seuil commun de la boutique)
                }
                · Stock {{ formatFactor(u.stockQuantity) }} {{ baseName() }}
              </div>
            </div>

            <!-- On sale or not: nothing is deleted, the tills only stop offering it. -->
            <div class="un-sale" [class.un-off]="tab() !== 'fiche'">
              @if (u.retired) {
                <span class="badge grey">Retiré de la vente</span>
                <span class="muted">Les caisses et les commandes ne le proposent plus ; son stock et son historique restent.</span>
                <button class="btn small" type="button" [disabled]="busy()" (click)="setRetired(false)">
                  Remettre en vente
                </button>
              } @else {
                <span class="muted">En vente aux caisses et aux commandes.</span>
                <button class="btn small ghost danger" type="button" [disabled]="busy()" (click)="retiring.set(true)">
                  Retirer de la vente
                </button>
              }
            </div>

            <div class="un-base" [class.un-off]="tab() !== 'unites'">
              <div class="fld">
                <label for="un-base">Unité de base (stock)</label>
                <input
                  id="un-base"
                  type="text"
                  autocomplete="off"
                  maxlength="16"
                  placeholder="kapoka, pièce, kg…"
                  [ngModel]="baseDraft()"
                  (ngModelChange)="baseDraft.set($event)"
                  (keydown.enter)="saveBase()"
                />
              </div>
              @if (baseChanged()) {
                <button class="btn small" type="button" [disabled]="busy()" (click)="saveBase()">
                  Renommer
                </button>
              }
              <div class="un-base-facts muted">
                Prix {{ u.basePrice | ariary }} · Stock {{ formatFactor(u.stockQuantity) }} {{ baseName() }}
              </div>
            </div>

            <!-- ---------------------------------------------------- every unit -->
            <!-- Each unit priced both ways: what it sells for, what it cost at the average, and
                 what is left. Worked out by the server, so a tonne reads back to the ariary.
                 A table on a wide screen; on a narrow one each row becomes a card (data-label),
                 so the dialog never scrolls sideways. -->
            <app-barcodes-editor [class.un-off]="tab() !== 'codes'" [units]="u" (changed)="onCodes($event)" />

            <table class="un-table" [class.un-off]="tab() === 'fiche' || tab() === 'codes'">
              <thead>
                <tr>
                  <th>Unité</th>
                  <th>Contient</th>
                  <th class="num">Prix de vente</th>
                  <th class="num">Prix d'entrée</th>
                  <th class="num">Bénéfice</th>
                  <th>Code-barres</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr class="un-base-row">
                  <td class="un-unit">
                    {{ baseName() }} <span class="muted">(base)</span>
                    @if (u.purchaseUnitId === null) {
                      <span class="badge grey">achat</span>
                    }
                  </td>
                  <td class="muted" data-label="Contient">1 {{ baseName() }}</td>
                  <td class="num" data-label="Prix de vente">{{ u.basePrice | ariary }}</td>
                  <ng-container
                    *ngTemplateOutlet="costCells; context: { $implicit: costOf(null) }"
                  />
                  <td class="muted un-code" data-label="Code-barres">{{ u.baseBarcode ?? '—' }}</td>
                  <td class="un-actions"></td>
                </tr>
                @for (p of u.packagings; track p.id) {
                  <tr [class.on]="editingId() === p.id">
                    <td class="un-unit">
                      {{ p.label }}
                      @if (u.purchaseUnitId === p.id) {
                        <span class="badge grey">achat</span>
                      }
                    </td>
                    <td data-label="Contient">
                      @if (p.reference) {
                        {{ formatFactor(p.referenceQuantity) }} {{ p.reference }}
                        <span class="muted">({{ formatFactor(p.factor) }} {{ baseName() }})</span>
                      } @else {
                        {{ formatFactor(p.factor) }} {{ baseName() }}
                      }
                    </td>
                    <td class="num" data-label="Prix de vente">{{ p.price | ariary }}</td>
                    <ng-container
                      *ngTemplateOutlet="costCells; context: { $implicit: costOf(p.id) }"
                    />
                    <td class="muted un-code" data-label="Code-barres">{{ p.barcode ?? '—' }}</td>
                    <td class="un-actions">
                      @if (removingId() === p.id) {
                        <span class="muted">Supprimer ?</span>
                        <button class="btn small danger" type="button" [disabled]="busy()" (click)="remove(p)">
                          Oui
                        </button>
                        <button class="btn small ghost" type="button" (click)="removingId.set(null)">
                          Non
                        </button>
                      } @else {
                        <button class="btn small ghost" type="button" (click)="edit(p)">Modifier</button>
                        <button class="btn small ghost" type="button" (click)="removingId.set(p.id)">
                          Supprimer
                        </button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr class="un-empty-row">
                    <td colspan="7" class="muted un-empty">
                      Vendu dans une seule unité. Ajoutez-en une autre ci-dessous si le produit se
                      vend ou s'achète aussi au kilo, au carton, au sac, à la tonne…
                    </td>
                  </tr>
                }
              </tbody>
            </table>

            <ng-template #costCells let-cost>
              @if (cost?.cost != null) {
                <td class="num" data-label="Prix d'entrée">{{ cost.cost | ariary }}</td>
                <td class="num" data-label="Bénéfice" [class.un-loss]="cost.margin < 0">
                  {{ cost.margin | ariary }}
                  @if (cost.marginRate !== null) {
                    <span class="muted">· {{ formatRate(cost.marginRate) }}</span>
                  }
                </td>
              } @else {
                <td class="num muted" data-label="Prix d'entrée">—</td>
                <td class="num muted" data-label="Bénéfice">—</td>
              }
            </ng-template>

            <!-- --------------------------------------------- units from a model -->
            <!-- g, kg, tonne… ticked rather than typed again for every product. -->
            <details class="un-model" [class.un-off]="tab() !== 'unites'" [open]="modelOpen()" (toggle)="onModelToggle($event)">
              <summary>Ajouter des unités depuis un modèle (g, kg, tonne…)</summary>
              @if (modelOpen()) {
                <app-unit-model-picker
                  #picker
                  fieldId="un-model"
                  [baseUnit]="u.baseUnit"
                  [basePrice]="u.basePrice"
                  [packagings]="u.packagings"
                  (manage)="modelsOpen.set(true)"
                />
                <div class="un-form-foot">
                  <div class="un-hint muted"></div>
                  <button
                    class="btn small"
                    type="button"
                    [disabled]="busy() || !picker.request()"
                    (click)="applyModel(picker)"
                  >
                    Ajouter les unités cochées
                  </button>
                </div>
              }
            </details>

            <!-- ------------------------------------------------------- the form -->
            <form class="un-form" [class.un-off]="tab() !== 'unites'" (ngSubmit)="save()">
              <div class="un-form-title">
                {{ editingId() === null ? 'Ajouter une unité' : 'Modifier « ' + editingLabel() + ' »' }}
              </div>
              @if (editingId() === null) {
                <!-- The usual packagings, one tap away: the name is filled in, the content is still asked. -->
                <div class="un-chips" role="group" aria-label="Unités courantes">
                  @for (chip of unitChips; track chip) {
                    <button
                      type="button"
                      class="un-chip"
                      [class.on]="draft().label === chip"
                      (click)="pickChip(chip)"
                    >
                      {{ chip }}
                    </button>
                  }
                </div>
              }
              <div class="un-fields">
                <div class="fld">
                  <label for="un-label">Unité</label>
                  <input
                    id="un-label"
                    name="label"
                    type="text"
                    autocomplete="off"
                    maxlength="16"
                    placeholder="kg"
                    [ngModel]="draft().label"
                    (ngModelChange)="patch({ label: $event })"
                  />
                </div>
                <div class="fld">
                  <label for="un-factor">Contient</label>
                  <div class="un-suffixed">
                    <input
                      id="un-factor"
                      name="factor"
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      placeholder="3,5"
                      [ngModel]="draft().factor"
                      (ngModelChange)="patch({ factor: $event })"
                    />
                    <!-- Written the way it is printed: a 25 kg sack contains 25 kg, and the
                         server works out the kapoka. -->
                    <select
                      name="reference"
                      class="field"
                      aria-label="Unité de référence"
                      [ngModel]="draft().reference"
                      (ngModelChange)="patch({ reference: $event })"
                    >
                      <option [ngValue]="null">{{ baseName() }}</option>
                      @for (p of referenceChoices(); track p.id) {
                        <option [ngValue]="p.label">{{ p.label }}</option>
                      }
                    </select>
                  </div>
                </div>
                <div class="fld">
                  <label for="un-price">Prix (Ar)</label>
                  <input
                    id="un-price"
                    name="price"
                    type="text"
                    inputmode="decimal"
                    autocomplete="off"
                    [placeholder]="suggestedPrice() ?? '3000'"
                    [ngModel]="draft().price"
                    (ngModelChange)="patch({ price: $event })"
                  />
                </div>
                <div class="fld">
                  <label for="un-barcode">Code-barres</label>
                  <div class="un-suffixed">
                    <input
                      id="un-barcode"
                      name="barcode"
                      type="text"
                      autocomplete="off"
                      placeholder="facultatif"
                      [ngModel]="draft().barcode"
                      (ngModelChange)="patch({ barcode: $event })"
                      (keydown.enter)="$event.preventDefault()"
                    />
                    <app-camera-scan-button (scanned)="patch({ barcode: $event })" />
                  </div>
                </div>
              </div>

              <!-- A unit typed here once can be kept in a model, to be ticked on the next product. -->
              @if (editingId() === null) {
                <div class="un-keep">
                  <label class="un-keep-check">
                    <input
                      type="checkbox"
                      name="keep"
                      [ngModel]="keep().on"
                      (ngModelChange)="patchKeep({ on: $event })"
                    />
                    Ajouter aussi au modèle
                  </label>
                  @if (keep().on) {
                    <select
                      name="keepModel"
                      class="field"
                      aria-label="Modèle"
                      [ngModel]="keep().modelId"
                      (ngModelChange)="patchKeep({ modelId: $event })"
                    >
                      @for (m of keepChoices(); track m.id) {
                        <option [ngValue]="m.id">{{ m.name }}</option>
                      }
                      <option [ngValue]="null">＋ Nouveau modèle…</option>
                    </select>
                    @if (keep().modelId === null) {
                      <input
                        name="keepName"
                        type="text"
                        maxlength="40"
                        aria-label="Nom du nouveau modèle"
                        [placeholder]="defaultModelName()"
                        [ngModel]="keep().name"
                        (ngModelChange)="patchKeep({ name: $event })"
                      />
                    }
                  }
                </div>
              }

              <div class="un-form-foot">
                <div class="un-hint muted">
                  @if (preview(); as line) {
                    {{ line }}
                  }
                </div>
                @if (editingId() !== null) {
                  <button class="btn small ghost" type="button" (click)="reset()">Annuler</button>
                }
                <button class="btn small" type="submit" [disabled]="busy() || !request()">
                  {{ editingId() === null ? 'Ajouter' : 'Enregistrer' }}
                </button>
              </div>
            </form>
          } @else {
            <div class="empty">Chargement…</div>
          }
        </div>

        <div class="modal-foot">
          <!-- The stock manager books goods in from here too: the receipt opens in the unit the
               product is bought in, at the last price paid. -->
          @if (rights.canAddStock() && units(); as u) {
            <button class="btn ghost" type="button" (click)="addStock.emit(asProduct(u))">
              ＋ Ajouter du stock
            </button>
          }
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>

    @if (retiring()) {
      <app-confirm-dialog
        title="Retirer de la vente ?"
        [message]="'« ' + (units()?.productName ?? productName) + ' » ne sera plus proposé aux caisses ni aux commandes. Son stock, son historique et ses factures restent ; il peut être remis en vente.'"
        confirmLabel="Retirer de la vente"
        [danger]="true"
        [busy]="busy()"
        (confirmed)="setRetired(true)"
        (closed)="retiring.set(false)"
      />
    }

    @if (modelsOpen()) {
      <app-unit-models-dialog (changed)="onModelsChanged()" (closed)="modelsOpen.set(false)" />
    }
  `,
  styles: `
    /* Wide enough for the seven columns of the units on a computer; on a phone the dialog takes
       the whole screen and the table turns into cards. Never a sideways scroll. */
    .modal.cfg {
      width: min(1040px, 100%);
    }

    .cfg-title {
      min-width: 0;
    }

    .cfg-help {
      font-size: 12.5px;
      margin-bottom: 4px;

      summary {
        cursor: pointer;
        width: fit-content;
      }
    }

    /* The sheet: as many fields a line as fit, never narrower than a price. */
    /* A part of the sheet that belongs to another tab. */
    .un-off {
      display: none !important;
    }

    .cfg-tabs {
      display: block;
      margin-bottom: 14px;
    }

    .un-sale {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      margin-bottom: 14px;
      font-size: 13px;
    }

    .un-alert {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: 10px;
      margin-bottom: 14px;

      .fld {
        margin-bottom: 0;
        width: 220px;
        max-width: 100%;
      }
    }

    .un-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 10px;
    }

    .un-chip {
      border: 1px solid var(--line);
      border-radius: 14px;
      background: var(--panel);
      color: inherit;
      font: inherit;
      font-size: 12.5px;
      padding: 3px 11px;
      cursor: pointer;

      &:hover,
      &.on {
        border-color: var(--brand);
        background: var(--brand-soft);
      }
    }

    .un-sheet {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
      align-items: end;
      gap: 10px;
      margin-bottom: 14px;
      padding-bottom: 14px;
      border-bottom: 1px solid var(--line);

      .fld {
        margin-bottom: 0;
        min-width: 0;
      }

      .un-sheet-name {
        grid-column: span 2;
      }

      .un-cost {
        grid-column: span 2;
      }
    }

    .un-cost-hint {
      grid-column: 1 / -1;
      font-size: 12px;
    }

    .un-sheet-foot {
      grid-column: 1 / -1;
      display: flex;
      justify-content: flex-end;
    }

    .un-loss {
      color: var(--red);
    }

    .un-base {
      display: flex;
      align-items: end;
      gap: 10px;
      flex-wrap: wrap;
      margin-bottom: 14px;

      .fld {
        margin-bottom: 0;
        flex: 0 1 220px;
        min-width: 0;
      }
    }

    .un-base-facts {
      font-size: 12px;
      align-self: center;
      margin-left: auto;
    }

    .un-table {
      width: 100%;
      margin-bottom: 16px;
      table-layout: auto;

      tr.on {
        background: var(--brand-soft);
      }

      td {
        overflow-wrap: anywhere;
      }
    }

    .un-code {
      word-break: break-all;
    }

    .un-base-row td {
      background: var(--bg);
    }

    .un-actions {
      text-align: right;
      white-space: nowrap;

      .btn + .btn {
        margin-left: 6px;
      }
    }

    .un-empty {
      font-size: 12.5px;
      padding: 12px 8px;
      white-space: normal;
    }

    .un-model {
      border: 1px solid var(--line);
      border-radius: 9px;
      padding: 10px 12px;
      margin-bottom: 12px;

      summary {
        cursor: pointer;
        font-weight: 700;
        font-size: 12.5px;
      }

      &[open] summary {
        margin-bottom: 10px;
      }
    }

    .un-form {
      border: 1px solid var(--line);
      border-radius: 9px;
      padding: 12px;
    }

    .un-form-title {
      font-weight: 700;
      font-size: 12.5px;
      margin-bottom: 10px;
    }

    .un-fields {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
      gap: 10px;

      .fld {
        margin-bottom: 0;
        min-width: 0;
      }
    }

    .un-suffixed {
      display: flex;
      align-items: center;
      gap: 6px;

      input {
        min-width: 0;
        flex: 1;
      }

      select {
        width: auto;
        min-width: 0;
        max-width: 50%;
      }
    }

    .un-keep {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      margin-top: 10px;
      font-size: 13px;

      select,
      input[type='text'] {
        width: auto;
        min-width: 0;
        flex: 0 1 220px;
      }
    }

    .un-keep-check {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
    }

    .un-form-foot {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 10px;
    }

    .un-hint {
      flex: 1 1 200px;
      font-size: 12px;
    }

    /* A phone, or a narrow window: the dialog fills the screen, its head and foot stay in sight,
       and each unit is a card — its name as a title, then one line per figure. */
    @media (max-width: 760px) {
      .cfg-backdrop {
        padding: 0;
      }

      .modal.cfg {
        width: 100%;
        height: 100%;
        max-height: 100%;
        border-radius: 0;
        display: flex;
        flex-direction: column;

        .modal-body {
          flex: 1;
          overflow-y: auto;
          padding: 14px;
        }
      }

      .un-sheet .un-sheet-name,
      .un-sheet .un-cost {
        grid-column: 1 / -1;
      }

      .un-base-facts {
        margin-left: 0;
      }

      .un-table {
        thead {
          display: none;
        }

        tbody,
        tr,
        td {
          display: block;
        }

        tr {
          border: 1px solid var(--line);
          border-radius: 9px;
          padding: 8px 10px;
          margin-bottom: 8px;
        }

        td {
          border: none;
          padding: 3px 0;
          text-align: left;
        }

        /* The figure on the right, kept in one piece ("200 Ar · 22,2 %"); its label on the left. */
        td[data-label] {
          text-align: right;

          &::before {
            content: attr(data-label);
            float: left;
            margin-right: 12px;
            color: var(--ink-soft);
            font-size: 12px;
          }
        }

        .un-unit {
          font-weight: 700;
          padding-bottom: 6px;
        }

        .un-actions {
          text-align: left;
          white-space: normal;
          padding-top: 8px;
        }

        .un-actions:empty {
          display: none;
        }

        .un-empty-row {
          border-style: dashed;
        }
      }
    }
  `,
})
export class ProductUnitsDialogComponent implements OnInit {
  private readonly api = inject(CatalogApi);
  private readonly costing = inject(CostingApi);
  private readonly modelApi = inject(UnitModelApi);
  private readonly toasts = inject(ToastService);
  protected readonly rights = inject(ProductRights);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input({ required: true }) productId!: number;
  /** Shown while the units load, so the title never flashes empty. */
  @Input() productName = '';
  /** Opened to give a purchase price ("partiel" on the profit page): the cursor waits in it. */
  @Input() focusCost = false;
  /** Opened from "Seuil d'alerte" in the menu: the cursor waits in the threshold. */
  @Input() focusThreshold = false;
  /** The tab it opens on — the menu entry picked says which. */
  @Input() set initialTab(tab: ConfigTab | undefined) {
    if (tab) {
      this.tab.set(tab);
    }
  }

  protected readonly tab = signal<ConfigTab>('fiche');
  protected readonly tabs: readonly FilterTab<ConfigTab>[] = [
    { value: 'fiche', label: 'Fiche' },
    { value: 'prix', label: 'Prix' },
    { value: 'unites', label: 'Unités et packaging' },
    { value: 'codes', label: 'Codes-barres' },
  ];
  /** After any successful write: the stock list may show the base unit's new name. */
  @Output() readonly changed = new EventEmitter<void>();
  @Output() readonly closed = new EventEmitter<void>();
  /** "Ajouter du stock" from the sheet: the product as it now stands, units included. */
  @Output() readonly addStock = new EventEmitter<Product>();

  protected readonly units = signal<ProductUnits | null>(null);
  protected readonly busy = signal(false);
  protected readonly baseDraft = signal('');
  /** The alert threshold as typed; empty for the shop's common one. */
  protected readonly alertDraft = signal('');
  /** What the threshold field would save: a number, null for the common one, undefined if unreadable. */
  private readonly alertValue = computed<number | null | undefined>(() => {
    const typed = this.alertDraft().trim();
    if (!typed) {
      return null;
    }
    const value = Number(typed.replace(',', '.'));
    return Number.isFinite(value) && value >= 0 ? value : undefined;
  });
  protected readonly alertValid = computed(() => this.alertValue() !== undefined);
  protected readonly alertChanged = computed(
    () => this.alertValue() !== (this.units()?.lowStockThreshold ?? null),
  );
  protected readonly draft = signal<Draft>(EMPTY_DRAFT);
  protected readonly editingId = signal<number | null>(null);
  protected readonly removingId = signal<number | null>(null);

  /** « Ajouter des unités depuis un modèle » unfolded. */
  protected readonly modelOpen = signal(false);
  /** The models dialog, over this one. */
  protected readonly modelsOpen = signal(false);
  protected readonly keep = signal<Keep>(KEEP_OFF);
  private readonly picker = viewChild(UnitModelPickerComponent);
  private readonly modelResource = apiResource<UnitModel[]>([], () => this.modelApi.models());

  /** The unit the one being typed is written in, by name: what a model must have to take it. */
  private readonly keepReference = computed(() => this.draft().reference ?? this.units()?.baseUnit ?? null);

  /** The models that know that unit: the new one can be written in it there too. */
  protected readonly keepChoices = computed(() => {
    const reference = this.keepReference();
    if (!reference) {
      return [];
    }
    const folded = fold(reference);
    return this.modelResource
      .value()
      .filter((m) => fold(m.baseLabel) === folded || m.units.some((u) => fold(u.label) === folded));
  });

  protected readonly defaultModelName = computed(() => `Unités en ${this.keepReference() ?? 'unité'}`);

  /** What the base unit is called in sentences; "unité" for a product counted in bare units. */
  protected readonly baseName = computed(() => this.units()?.baseUnit ?? 'unité');

  protected readonly baseChanged = computed(
    () => this.baseDraft().trim() !== (this.units()?.baseUnit ?? ''),
  );

  protected readonly sheet = signal<Sheet>(EMPTY_SHEET);

  /** Every unit priced both ways, as the server last worked it out; null until it answers. */
  private readonly costs = signal<ProductCost | null>(null);

  private readonly categoryResource = apiResource<CategoryNode[]>([], () => this.api.categories());
  protected readonly categories = this.categoryResource.value;

  /**
   * The purchase cost typed, or null when the field still shows what the server holds for the
   * unit picked — then nothing is corrected, and switching the unit only changes how it is shown.
   */
  private readonly typedCost = computed<number | null | undefined>(() => {
    const text = this.sheet().cost.trim();
    if (!text) {
      return null;
    }
    const cost = parseDecimal(text);
    if (cost === null) {
      return undefined;
    }
    return cost === this.costOf(this.sheet().purchaseUnitId)?.cost ? null : cost;
  });

  /** The sheet as a request, or null while it cannot be sent. The server still judges it. */
  protected readonly sheetRequest = computed<UpdateProductRequest | null>(() => {
    const sheet = this.sheet();
    const price = parseDecimal(sheet.price);
    const cost = this.typedCost();
    if (!sheet.name.trim() || price === null || price <= 0 || cost === undefined) {
      return null;
    }
    return {
      name: sheet.name.trim(),
      price,
      barcode: sheet.barcode.trim() || null,
      categoryId: sheet.categoryId,
      // An emptied field is not "free": the average stays as the receipts made it.
      purchaseCost: cost,
      purchaseUnitId: sheet.purchaseUnitId,
    };
  });

  protected readonly sheetChanged = computed(() => {
    const units = this.units();
    const sheet = this.sheet();
    return (
      units !== null &&
      (sheet.name.trim() !== units.productName ||
        parseDecimal(sheet.price) !== units.basePrice ||
        sheet.barcode.trim() !== (units.baseBarcode ?? '') ||
        sheet.categoryId !== (units.category?.id ?? null) ||
        sheet.purchaseUnitId !== units.purchaseUnitId ||
        this.typedCost() !== null)
    );
  });

  /**
   * The purchase price read back per base unit — "soit 785,71 Ar le kapoka" — so the operator
   * sees what was understood without ever typing it.
   */
  protected readonly costHint = computed(() => {
    const units = this.units();
    const sheet = this.sheet();
    const cost = parseDecimal(sheet.cost.trim());
    const unit = units?.packagings.find((p) => p.id === sheet.purchaseUnitId);
    if (!units || !unit || cost === null) {
      return null;
    }
    const perBase = (cost / unit.factor).toLocaleString('fr-FR', { maximumFractionDigits: 2 });
    return `1 ${unit.label} = ${this.formatFactor(unit.factor)} ${this.baseName()} : soit ${perBase} Ar le ${this.baseName()}.`;
  });

  protected readonly editingLabel = computed(
    () => this.units()?.packagings.find((p) => p.id === this.editingId())?.label ?? '',
  );

  /** The form as a request, or null while it cannot be sent. The server still judges it. */
  protected readonly request = computed<PackagingRequest | null>(() => {
    const draft = this.draft();
    const factor = parseDecimal(draft.factor);
    const price = parseDecimal(draft.price);
    if (!draft.label.trim() || factor === null || factor <= 0 || price === null || price <= 0) {
      return null;
    }
    return {
      label: draft.label.trim(),
      factor,
      price,
      barcode: draft.barcode.trim() || null,
      reference: draft.reference,
    };
  });

  /**
   * The units the one being written may be counted in: the others, minus any written — directly
   * or through others — in it, which would go round in a circle. The server refuses those too.
   */
  protected readonly referenceChoices = computed(() => {
    const packagings = this.units()?.packagings ?? [];
    const editing = this.editingId();
    if (editing === null) {
      return packagings;
    }
    const byLabel = new Map(packagings.map((p) => [p.label, p]));
    const leadsToEditing = (p: Packaging): boolean => {
      for (let step: Packaging | undefined = p; step; step = step.reference ? byLabel.get(step.reference) : undefined) {
        if (step.id === editing) {
          return true;
        }
      }
      return false;
    };
    return packagings.filter((p) => !leadsToEditing(p));
  });

  /** Base units in one of the unit being written: its contenance times its reference's. */
  protected readonly baseFactor = computed(() => {
    const draft = this.draft();
    const quantity = parseDecimal(draft.factor);
    if (!quantity) {
      return null;
    }
    const reference = this.units()?.packagings.find((p) => p.label === draft.reference);
    return Math.round(quantity * (reference?.factor ?? 1) * 1000) / 1000;
  });

  /**
   * The base price times the factor, offered as the price field's placeholder — a starting point
   * the operator rounds, never a value filled in for them: a sack's price is a wholesale decision.
   */
  protected readonly suggestedPrice = computed(() => {
    const factor = this.baseFactor();
    const base = this.units()?.basePrice;
    return factor && base ? String(Math.round(factor * base)) : null;
  });

  /**
   * The form read back as a sentence: "1 kg = 3,5 kapoka · 857 Ar le kapoka", or for a unit
   * written in another, "1 sac 25 kg = 25 kg = 87,5 kapoka".
   */
  protected readonly preview = computed(() => {
    const draft = this.draft();
    const quantity = parseDecimal(draft.factor);
    const factor = this.baseFactor();
    if (!draft.label.trim() || !quantity || !factor) {
      return null;
    }
    const written = draft.reference ? ` = ${this.formatFactor(quantity)} ${draft.reference}` : '';
    const sentence = `1 ${draft.label.trim()}${written} = ${this.formatFactor(factor)} ${this.baseName()}`;
    const price = parseDecimal(draft.price);
    if (!price || price <= 0) {
      return sentence;
    }
    const perBase = Math.round(price / factor).toLocaleString('fr-FR');
    return `${sentence} · soit ${perBase} Ar le ${this.baseName()}`;
  });

  ngOnInit(): void {
    this.run(this.api.productUnits(this.productId), false);
  }

  /**
   * The purchase cost is served apart from the units, which every role reads: it is fetched
   * after each answer, since a save may just have changed it.
   */
  private loadCost(): void {
    this.costing.productCost(this.productId).subscribe((costs) => {
      this.costs.set(costs);
      this.patchSheet({ cost: this.costText(this.sheet().purchaseUnitId) });
      if (this.focusCost) {
        // Once: a later save reloads the cost, and the cursor may be elsewhere by then.
        this.focusCost = false;
        this.tab.set('prix');
        setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('#un-cost')?.focus());
      }
    });
  }

  /** The unit's sale price, cost and profit as the server worked them out; null while unknown. */
  protected costOf(packagingId: number | null): UnitCost | null {
    return this.costs()?.units.find((unit) => unit.packagingId === packagingId) ?? null;
  }

  /** A price typed for a kapoka is not a tonne's: the field shows what the new unit costs. */
  protected pickPurchaseUnit(packagingId: number | null): void {
    this.patchSheet({ purchaseUnitId: packagingId, cost: this.costText(packagingId) });
  }

  private costText(packagingId: number | null): string {
    const cost = this.costOf(packagingId)?.cost;
    return cost == null ? '' : String(cost);
  }

  /** 8.3 → "8,3 %". */
  protected formatRate(rate: number): string {
    return `${rate.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
  }

  /**
   * The product as the stock entry needs it, from what this sheet holds — fresher than the list
   * it was opened from, since a unit may just have been added here.
   */
  protected asProduct(units: ProductUnits): Product {
    return {
      id: units.productId,
      name: units.productName,
      price: units.basePrice,
      stockQuantity: units.stockQuantity,
      unit: units.baseUnit,
      barcode: units.baseBarcode,
      category: units.category,
      lowStock: false,
      stockValue: 0,
      packagings: units.packagings,
    };
  }

  protected formatFactor(factor: number): string {
    return factor.toLocaleString('fr-FR', { maximumFractionDigits: 3 });
  }

  /** The packagings most shops sell in, offered as chips above the form. */
  protected readonly unitChips = UNIT_CHIPS;

  /** A chip names the unit; what it contains depends on the product, so the cursor goes there. */
  protected pickChip(label: string): void {
    this.patch({ label });
    setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('#un-factor')?.focus());
  }

  /** "Retirer de la vente" asked, waiting for its confirmation. */
  protected readonly retiring = signal(false);

  protected setRetired(retired: boolean): void {
    if (this.busy()) {
      return;
    }
    this.run(this.api.changeRetired(this.productId, retired), true, () => {
      this.retiring.set(false);
      this.toasts.show(retired ? 'Produit retiré de la vente.' : 'Produit remis en vente.');
    });
  }

  /** A code saved in the codes tab: the sheet and the table read the units again. */
  protected onCodes(units: ProductUnits): void {
    this.run(of(units));
  }

  protected saveAlert(): void {
    const threshold = this.alertValue();
    if (threshold === undefined || !this.alertChanged() || this.busy()) {
      return;
    }
    this.run(this.api.changeAlertThreshold(this.productId, threshold), true, () =>
      this.toasts.show(
        threshold === null ? 'Seuil commun de la boutique rétabli.' : `Seuil d'alerte : ${this.formatFactor(threshold)} ${this.baseName()}.`,
      ),
    );
  }

  protected patch(change: Partial<Draft>): void {
    this.draft.update((draft) => ({ ...draft, ...change }));
  }

  protected patchSheet(change: Partial<Sheet>): void {
    this.sheet.update((sheet) => ({ ...sheet, ...change }));
  }

  protected saveSheet(): void {
    const request = this.sheetRequest();
    if (!request || !this.sheetChanged() || this.busy()) {
      return;
    }
    this.run(this.api.updateProduct(this.productId, request));
  }

  protected saveBase(): void {
    if (!this.baseChanged() || this.busy()) {
      return;
    }
    this.run(this.api.renameBaseUnit(this.productId, this.baseDraft().trim()));
  }

  protected edit(packaging: Packaging): void {
    this.removingId.set(null);
    this.editingId.set(packaging.id);
    this.draft.set({
      label: packaging.label,
      factor: this.formatFactor(packaging.referenceQuantity ?? packaging.factor),
      reference: packaging.reference ?? null,
      price: String(packaging.price),
      barcode: packaging.barcode ?? '',
    });
  }

  protected reset(): void {
    this.editingId.set(null);
    this.draft.set(EMPTY_DRAFT);
  }

  protected save(): void {
    const request = this.request();
    if (!request || this.busy()) {
      return;
    }
    const id = this.editingId();
    const keep = id === null && this.keep().on ? this.keep() : null;
    const reference = this.keepReference();
    this.run(
      id === null
        ? this.api.addPackaging(this.productId, request)
        : this.api.updatePackaging(this.productId, id, request),
      true,
      () => {
        this.reset();
        if (keep && reference) {
          this.keepInModel(keep, request, reference);
        }
      },
    );
  }

  /**
   * The unit just added, kept in a model as it was written — « sac 25 kg » contient 25 « kg » — so
   * the next product only ticks it. A model that does not know its reference refuses it: the toast
   * says so, and the product keeps its unit all the same.
   */
  private keepInModel(keep: Keep, request: PackagingRequest, reference: string): void {
    const unit = { label: request.label, quantity: request.factor, reference };
    const call =
      keep.modelId === null
        ? this.modelApi.create({
            name: keep.name.trim() || this.defaultModelName(),
            baseLabel: reference,
            units: [{ ...unit, reference: null }],
          })
        : this.modelApi.addUnit(keep.modelId, unit);
    call.subscribe((model) => {
      this.toasts.show(`« ${request.label} » ajoutée au modèle « ${model.name} ».`);
      this.keep.set(KEEP_OFF);
      this.onModelsChanged();
    });
  }

  protected patchKeep(change: Partial<Keep>): void {
    this.keep.update((keep) => {
      const next = { ...keep, ...change };
      // Ticked: the first model that knows the unit, else a new one.
      if (change.on && !keep.on) {
        next.modelId = this.keepChoices()[0]?.id ?? null;
      }
      return next;
    });
  }

  /** The ticked units of the model, onto this product; the picker is cleared once they are in. */
  protected applyModel(picker: UnitModelPickerComponent): void {
    const request = picker.request();
    if (!request || this.busy()) {
      return;
    }
    this.run(this.modelApi.applyToProduct(this.productId, request), true, () => picker.clear());
  }

  protected onModelToggle(event: Event): void {
    this.modelOpen.set((event.target as HTMLDetailsElement).open);
  }

  /** A model added, renamed or removed: the lists that show models read them again. */
  protected onModelsChanged(): void {
    this.modelResource.reload();
    this.picker()?.reload();
  }

  /** Échap closes the models dialog first when it is open over this one. */
  protected onEscape(): void {
    if (this.modelsOpen()) {
      this.modelsOpen.set(false);
    } else {
      this.closed.emit();
    }
  }

  protected remove(packaging: Packaging): void {
    this.run(this.api.removePackaging(this.productId, packaging.id), true, () => {
      this.removingId.set(null);
      if (this.editingId() === packaging.id) {
        this.reset();
      }
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }

  /**
   * Every call answers the whole set of units, so each one ends the same way: show what the server
   * holds. A refusal keeps the form as typed — the error toast says what to fix.
   */
  private run(call: Observable<ProductUnits>, write = true, after?: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: (units) => {
        this.units.set(units);
        this.baseDraft.set(units.baseUnit ?? '');
        this.alertDraft.set(units.lowStockThreshold === null ? '' : String(units.lowStockThreshold));
        if (this.focusThreshold) {
          this.focusThreshold = false;
          setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('#un-alert')?.focus());
        }
        this.sheet.update((sheet) => ({
          ...sheet,
          name: units.productName,
          price: String(units.basePrice),
          barcode: units.baseBarcode ?? '',
          categoryId: units.category?.id ?? null,
          purchaseUnitId: units.purchaseUnitId,
        }));
        this.loadCost();
        this.busy.set(false);
        after?.();
        if (write) {
          this.changed.emit();
        }
      },
      error: () => this.busy.set(false),
    });
  }
}
