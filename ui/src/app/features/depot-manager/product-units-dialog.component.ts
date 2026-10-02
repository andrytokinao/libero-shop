import { Component, EventEmitter, Input, OnInit, Output, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { Packaging, PackagingRequest, ProductUnits } from '../../core/models';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** The add/edit form, as typed. Strings because an empty field is not zero. */
interface Draft {
  label: string;
  factor: string;
  price: string;
  barcode: string;
}

const EMPTY_DRAFT: Draft = { label: '', factor: '', price: '', barcode: '' };

/**
 * The units a product is sold in: its base unit, and "1 kg = 3.5 kapoka"-style packagings.
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
@Component({
  selector: 'app-product-units-dialog',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="units-title">
        <div class="modal-head">
          <div>
            <h2 id="units-title">Unités de vente — {{ productName }}</h2>
            <div class="sub muted">
              Le stock est compté dans l'unité de base. Chaque autre unité dit combien d'unités de
              base elle contient. Choisissez la plus petite comme unité de base.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (units(); as u) {
            <!-- ---------------------------------------------------- base unit -->
            <div class="un-base">
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
                Prix {{ u.basePrice | ariary }} · Stock {{ u.stockQuantity }} {{ baseName() }}
              </div>
            </div>

            <!-- ---------------------------------------------------- other units -->
            <table class="un-table">
              <thead>
                <tr>
                  <th>Unité</th>
                  <th>Contient</th>
                  <th class="num">Prix</th>
                  <th>Code-barres</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr class="un-base-row">
                  <td>{{ baseName() }} <span class="muted">(base)</span></td>
                  <td class="muted">1 {{ baseName() }}</td>
                  <td class="num">{{ u.basePrice | ariary }}</td>
                  <td class="muted">{{ u.baseBarcode ?? '—' }}</td>
                  <td></td>
                </tr>
                @for (p of u.packagings; track p.id) {
                  <tr [class.on]="editingId() === p.id">
                    <td>{{ p.label }}</td>
                    <td>{{ formatFactor(p.factor) }} {{ baseName() }}</td>
                    <td class="num">{{ p.price | ariary }}</td>
                    <td class="muted">{{ p.barcode ?? '—' }}</td>
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
                  <tr>
                    <td colspan="5" class="muted un-empty">
                      Vendu dans une seule unité. Ajoutez-en une autre ci-dessous si le produit se
                      vend aussi au kilo, au carton, au sac…
                    </td>
                  </tr>
                }
              </tbody>
            </table>

            <!-- ------------------------------------------------------- the form -->
            <form class="un-form" (ngSubmit)="save()">
              <div class="un-form-title">
                {{ editingId() === null ? 'Ajouter une unité' : 'Modifier « ' + editingLabel() + ' »' }}
              </div>
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
                    <span class="muted">{{ baseName() }}</span>
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
                  <input
                    id="un-barcode"
                    name="barcode"
                    type="text"
                    autocomplete="off"
                    placeholder="facultatif"
                    [ngModel]="draft().barcode"
                    (ngModelChange)="patch({ barcode: $event })"
                  />
                </div>
              </div>

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
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .un-base {
      display: flex;
      align-items: end;
      gap: 10px;
      flex-wrap: wrap;
      margin-bottom: 14px;

      .fld {
        margin-bottom: 0;
        width: 200px;
      }
    }

    .un-base-facts {
      font-size: 12px;
      align-self: center;
      margin-left: auto;
    }

    .un-table {
      margin-bottom: 16px;

      tr.on {
        background: var(--brand-soft);
      }
    }

    .un-base-row td {
      background: var(--bg);
    }

    .un-actions {
      display: flex;
      gap: 6px;
      justify-content: flex-end;
      align-items: center;
      white-space: nowrap;
    }

    .un-empty {
      font-size: 12.5px;
      padding: 12px 8px;
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
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 10px;

      .fld {
        margin-bottom: 0;
      }

      @media (max-width: 640px) {
        grid-template-columns: repeat(2, minmax(0, 1fr));
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

      span {
        font-size: 12px;
        white-space: nowrap;
      }
    }

    .un-form-foot {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 10px;
    }

    .un-hint {
      flex: 1;
      font-size: 12px;
    }
  `,
})
export class ProductUnitsDialogComponent implements OnInit {
  private readonly api = inject(CatalogApi);

  @Input({ required: true }) productId!: number;
  /** Shown while the units load, so the title never flashes empty. */
  @Input() productName = '';
  /** After any successful write: the stock list may show the base unit's new name. */
  @Output() readonly changed = new EventEmitter<void>();
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly units = signal<ProductUnits | null>(null);
  protected readonly busy = signal(false);
  protected readonly baseDraft = signal('');
  protected readonly draft = signal<Draft>(EMPTY_DRAFT);
  protected readonly editingId = signal<number | null>(null);
  protected readonly removingId = signal<number | null>(null);

  /** What the base unit is called in sentences; "unité" for a product counted in bare units. */
  protected readonly baseName = computed(() => this.units()?.baseUnit ?? 'unité');

  protected readonly baseChanged = computed(
    () => this.baseDraft().trim() !== (this.units()?.baseUnit ?? ''),
  );

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
    return { label: draft.label.trim(), factor, price, barcode: draft.barcode.trim() || null };
  });

  /**
   * The base price times the factor, offered as the price field's placeholder — a starting point
   * the operator rounds, never a value filled in for them: a sack's price is a wholesale decision.
   */
  protected readonly suggestedPrice = computed(() => {
    const factor = parseDecimal(this.draft().factor);
    const base = this.units()?.basePrice;
    return factor && base ? String(Math.round(factor * base)) : null;
  });

  /** The form read back as a sentence: "1 kg = 3,5 kapoka · 857 Ar le kapoka". */
  protected readonly preview = computed(() => {
    const draft = this.draft();
    const factor = parseDecimal(draft.factor);
    if (!draft.label.trim() || !factor || factor <= 0) {
      return null;
    }
    const sentence = `1 ${draft.label.trim()} = ${this.formatFactor(factor)} ${this.baseName()}`;
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

  protected formatFactor(factor: number): string {
    return factor.toLocaleString('fr-FR', { maximumFractionDigits: 3 });
  }

  protected patch(change: Partial<Draft>): void {
    this.draft.update((draft) => ({ ...draft, ...change }));
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
      factor: this.formatFactor(packaging.factor),
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
    this.run(
      id === null
        ? this.api.addPackaging(this.productId, request)
        : this.api.updatePackaging(this.productId, id, request),
      true,
      () => this.reset(),
    );
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

/** "3,5", "3.5", "1 750" → a number; anything else → null. */
function parseDecimal(value: string): number | null {
  const cleaned = value.replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d+)?$/.test(cleaned)) {
    return null;
  }
  return Number(cleaned);
}
