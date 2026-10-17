import { Component, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CategoryNode, CreateProductRequest, Product } from '../../core/models';
import { formatAmount } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';
import { CameraScanButtonComponent } from './camera-scan-button.component';
import { CategoryPickerComponent } from './category-picker.component';
import { UnitModelPickerComponent } from './unit-model-picker.component';
import { UnitModelsDialogComponent } from './unit-models-dialog.component';
import { HelpButtonComponent } from './help-button.component';

/** The form as typed. Strings because an empty field is not zero. */
interface Draft {
  name: string;
  price: string;
  unit: string;
  barcode: string;
  categoryId: number | null;
  cost: string;
  stock: string;
}

const EMPTY: Draft = { name: '', price: '', unit: '', barcode: '', categoryId: null, cost: '', stock: '' };

/**
 * A product typed in by hand, one at a time — for the shop that has no file to import, or one
 * new reference to add to a catalogue that is already there.
 *
 * <p>Only the name and the price are required. What is already on the shelf is asked here too,
 * and the server books it as an entry in the product's history, so its stock is explained from
 * the first unit. Units kept in a model — g, kg, tonne — can be ticked here, each at its price,
 * and are created with the product; any other unit is added afterwards from its menu.
 */
@Component({
  selector: 'app-product-create-dialog',
  standalone: true,
  imports: [
    HelpButtonComponent,
    FormsModule,
    CameraScanButtonComponent,
    CategoryPickerComponent,
    UnitModelPickerComponent,
    UnitModelsDialogComponent,
  ],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <form class="modal wide pn" role="dialog" aria-modal="true" aria-labelledby="pn-title" (ngSubmit)="create()">
        <div class="modal-head">
          <div>
            <h2 id="pn-title">Nouveau produit</h2>
            <div class="sub muted">
              Le nom et le prix suffisent. Les unités courantes (g, kg, tonne…) se cochent ci-dessous ;
              les autres s'ajoutent ensuite avec « Unités et packaging » dans le menu du produit.
            </div>
          </div>
          <app-help topic="product-create" />
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="fld">
            <label for="pn-name">Nom du produit</label>
            <input
              id="pn-name"
              name="name"
              type="text"
              autocomplete="off"
              maxlength="255"
              [ngModel]="draft().name"
              (ngModelChange)="patch({ name: $event })"
            />
          </div>
          <div class="pn-row">
            <div class="fld">
              <label for="pn-price">Prix de vente (Ar)</label>
              <input
                id="pn-price"
                name="price"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                [ngModel]="draft().price"
                (ngModelChange)="patch({ price: $event })"
              />
            </div>
            <div class="fld">
              <label for="pn-unit">Unité de base (stock)</label>
              <input
                id="pn-unit"
                name="unit"
                type="text"
                autocomplete="off"
                maxlength="16"
                placeholder="pièce, kg, bouteille…"
                [ngModel]="draft().unit"
                (ngModelChange)="patch({ unit: $event })"
              />
            </div>
          </div>
          <!-- Name, price and unit are all a product needs; the rest waits, folded, for whoever has it. -->
          <details class="pn-more" [open]="moreOpen">
            <summary>Plus de détails <span class="muted">— catégorie, code-barres, prix d'achat, stock de départ</span></summary>
          <div class="pn-row">
            <div class="fld">
              <label for="pn-category">Catégorie</label>
              <app-category-picker
                fieldId="pn-category"
                noneLabel="Sans catégorie"
                [categories]="categories()"
                [value]="draft().categoryId"
                (valueChange)="patch({ categoryId: $event })"
              />
            </div>
            <div class="fld">
              <label for="pn-barcode">Code-barres</label>
              <!-- Typed, read by a USB scanner, or by the phone camera. A scanner presses Entrée
                   after the code: it must not create the product half filled in. -->
              <div class="pn-scan">
                <input
                  id="pn-barcode"
                  name="barcode"
                  type="text"
                  autocomplete="off"
                  maxlength="64"
                  placeholder="scanner ou saisir"
                  [ngModel]="draft().barcode"
                  (ngModelChange)="patch({ barcode: $event })"
                  (keydown.enter)="$event.preventDefault()"
                />
                <app-camera-scan-button (scanned)="patch({ barcode: $event })" />
              </div>
            </div>
          </div>
          <div class="pn-row">
            <div class="fld">
              <label for="pn-cost">Prix d'achat (Ar) par {{ unitName() }}</label>
              <input
                id="pn-cost"
                name="cost"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                placeholder="facultatif"
                [ngModel]="draft().cost"
                (ngModelChange)="patch({ cost: $event })"
              />
            </div>
            <div class="fld">
              <label for="pn-stock">Stock de départ ({{ unitName() }})</label>
              <input
                id="pn-stock"
                name="stock"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                placeholder="0"
                [ngModel]="draft().stock"
                (ngModelChange)="patch({ stock: $event })"
              />
            </div>
          </div>
          </details>
          @if (warning(); as line) {
            <div class="muted pn-warning">{{ line }}</div>
          }

          <!-- Units kept in a model, ticked rather than typed again: created with the product. -->
          <details class="pn-units">
            <summary>Autres unités depuis un modèle (g, kg, tonne…)</summary>
            <app-unit-model-picker
              fieldId="pn-model"
              [baseUnit]="draft().unit"
              [basePrice]="basePrice()"
              (manage)="modelsOpen.set(true)"
            />
          </details>
        </div>

        <div class="modal-foot">
          @if (missing(); as what) {
            <!-- A grey button says nothing on its own: what it waits for is written beside it. -->
            <span class="pn-missing muted">Il manque : {{ what }}</span>
          }
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="submit" [disabled]="busy() || !request()">
            {{ busy() ? 'Enregistrement...' : 'Créer le produit' }}
          </button>
        </div>
      </form>
    </div>

    @if (modelsOpen()) {
      <app-unit-models-dialog (changed)="picker()?.reload()" (closed)="modelsOpen.set(false)" />
    }
  `,
  styles: `
    .pn-scan {
      display: flex;
      gap: 6px;

      input {
        min-width: 0;
        flex: 1;
      }

      /* No camera outside the installed app: no gap kept for it, the field as wide as the others. */
      app-camera-scan-button:empty {
        display: none;
      }
    }

    .pn-more {
      margin: 4px 0 10px;

      summary {
        cursor: pointer;
        font-weight: 600;
        font-size: 13px;
        margin-bottom: 10px;
      }
    }

    .pn-missing {
      margin-right: auto;
      font-size: 12.5px;
    }

    .pn-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;

      @media (max-width: 480px) {
        grid-template-columns: 1fr;
      }
    }

    .pn-units {
      border: 1px solid var(--line);
      border-radius: 9px;
      padding: 10px 12px;
      margin-top: 12px;

      summary {
        cursor: pointer;
        font-weight: 700;
        font-size: 12.5px;
      }

      &[open] summary {
        margin-bottom: 10px;
      }
    }

    /* A phone: the whole screen, nothing sideways. */
    @media (max-width: 560px) {
      .modal.pn {
        width: 100%;
        max-height: 100%;
      }
    }

    .pn-warning {
      font-size: 12.5px;
      color: var(--red);
    }
  `,
})
export class ProductCreateDialogComponent implements OnInit {
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);

  /** A code already read — an unknown code scanned at the till — filled in from the start. */
  readonly barcode = input('');
  /** "Dupliquer le produit": name, price, unit and rayon start from this one. */
  readonly copyOf = input<Product | null>(null);
  /** The screen says what follows the creation itself (with its own buttons): no message from here. */
  readonly announce = input(true);
  /** The product as the server created it. */
  readonly created = output<Product>();
  readonly closed = output<void>();

  protected readonly draft = signal<Draft>(EMPTY);
  protected readonly busy = signal(false);
  protected readonly modelsOpen = signal(false);
  protected readonly picker = viewChild(UnitModelPickerComponent);

  /** The base price as typed, for the prices the model proposes; null while not a number. */
  protected readonly basePrice = computed(() => amount(this.draft().price, 2) ?? null);

  private readonly categoryResource = apiResource<CategoryNode[]>([], () => this.api.categories());
  protected readonly categories = this.categoryResource.value;

  protected readonly unitName = computed(() => this.draft().unit.trim() || 'unité');

  /** The form as a request, or null while it cannot be sent. The server still judges it. */
  protected readonly request = computed<CreateProductRequest | null>(() => {
    const draft = this.draft();
    const price = amount(draft.price, 2);
    const cost = amount(draft.cost, 2);
    const stock = amount(draft.stock, 3);
    if (!draft.name.trim() || price === null || price === undefined || price <= 0) {
      return null;
    }
    if (cost === undefined || stock === undefined || this.picker()?.blocking()) {
      return null;
    }
    return {
      name: draft.name.trim(),
      price,
      unit: draft.unit.trim() || null,
      barcode: draft.barcode.trim() || null,
      categoryId: draft.categoryId,
      purchaseCost: cost,
      initialStock: stock,
      units: this.picker()?.request() ?? null,
    };
  });

  /** Sold at a loss from the first day: said, not refused — it may be meant. */
  protected readonly warning = computed(() => {
    const request = this.request();
    return request?.purchaseCost != null && request.purchaseCost >= request.price
      ? "Prix d'achat supérieur ou égal au prix de vente : ce produit serait vendu à perte."
      : null;
  });

  /** "Plus de détails" unfolded from the start when something is already filled in there. */
  protected moreOpen = false;

  /** What the creation still waits for, in words — null once it can be sent. */
  protected readonly missing = computed<string | null>(() => {
    const draft = this.draft();
    const price = amount(draft.price, 2);
    const absent = [
      !draft.name.trim() && 'le nom',
      !(price && price > 0) && 'un prix de vente',
      amount(draft.cost, 2) === undefined && "un prix d'achat en chiffres",
      amount(draft.stock, 3) === undefined && 'un stock de départ en chiffres',
      this.picker()?.blocking() && 'le choix des unités du modèle',
    ].filter(Boolean);
    return absent.length ? absent.join(' et ') : null;
  });

  /** Here rather than in a field: the inputs are not there yet before this. */
  ngOnInit(): void {
    this.moreOpen = !!this.barcode() || !!this.copyOf()?.category;
    if (this.barcode()) {
      this.patch({ barcode: this.barcode() });
    }
    const model = this.copyOf();
    if (model) {
      // The code and the stock are this product's own: a copy starts without them.
      this.patch({
        name: `${model.name} (copie)`,
        price: String(model.price),
        unit: model.unit ?? '',
        categoryId: model.category?.id ?? null,
      });
    }
  }

  protected patch(change: Partial<Draft>): void {
    this.draft.update((draft) => ({ ...draft, ...change }));
  }

  protected create(): void {
    const request = this.request();
    if (!request || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.createProduct(request).subscribe({
      next: (product) => {
        if (this.announce()) {
          this.toasts.show(
            product.stockQuantity > 0
              ? `${product.name} créé, avec ${formatAmount(product.stockQuantity, product.unit)} en stock.`
              : `${product.name} créé.`,
          );
        }
        this.created.emit(product);
        this.closed.emit();
      },
      // A refusal keeps the form as typed — the error toast says what to fix.
      error: () => this.busy.set(false),
    });
  }

  /** Échap closes the models dialog first when it is open over this one. */
  protected onEscape(): void {
    if (this.modelsOpen()) {
      this.modelsOpen.set(false);
    } else {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}

/**
 * "1 500", "1,5", "0.25" → a number; an empty field → null, "not given"; anything else →
 * undefined, which keeps the form from being sent.
 */
function amount(text: string, decimals: number): number | null | undefined {
  const cleaned = text.replace(/\s/g, '').replace(',', '.');
  if (!cleaned) {
    return null;
  }
  return new RegExp(`^\\d+(\\.\\d{1,${decimals}})?$`).test(cleaned) ? Number(cleaned) : undefined;
}
