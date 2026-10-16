import { Component, ElementRef, afterNextRender, inject, input, output, signal, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, of, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { BarcodeAssignment, CodeTarget, codeTargetsOf } from '../../core/catalog/barcode-assignment';
import { ProductRights } from '../../core/catalog/product-rights';
import { Product } from '../../core/models';
import { formatAmount } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';
import { ProductCreateDialogComponent } from './product-create-dialog.component';

/** Below this many letters a search names half the catalogue. */
const MIN_SEARCH = 2;
const RESULTS = 8;

/**
 * What to do with a scanned code no product carries — instead of a dead end.
 *
 * <p>Anyone may look the article up by its name and take it. Who may write the catalogue is also
 * offered to give the code to the product it belongs to — to its base unit or to a carton — or to
 * create the product with the code already filled in. Once given, the code is handed back
 * (`assigned`), so the screen enters it again and it now finds its product, in the right unit.
 */
@Component({
  selector: 'app-unknown-code-dialog',
  standalone: true,
  imports: [FormsModule, ProductCreateDialogComponent],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    @if (creating()) {
      <app-product-create-dialog
        [barcode]="code()"
        (created)="onCreated()"
        (closed)="creating.set(false)"
      />
    } @else {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="uc-title">
          <div class="modal-head">
            <div>
              <h2 id="uc-title">Code inconnu : {{ code() }}</h2>
              <div class="sub muted">
                @if (rights.canEdit()) {
                  Aucun produit ne porte ce code. Choisissez le produit auquel il appartient, ou
                  créez-le.
                } @else {
                  Aucun produit ne porte ce code. Cherchez l'article par son nom.
                }
              </div>
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
          </div>

          <div class="modal-body">
            @if (chosen(); as product) {
              <!-- Step 2, catalogue writers only: which unit of it the code is printed on. -->
              <p class="uc-lead">
                Ce code est imprimé sur quelle unité de <strong>{{ product.name }}</strong> ?
              </p>
              @if (targets(); as list) {
                <div class="uc-targets" role="radiogroup" aria-label="Unité">
                  @for (target of list; track target.packagingId) {
                    <label class="uc-target">
                      <input
                        type="radio"
                        name="uc-target"
                        [checked]="targetId() === target.packagingId"
                        (change)="targetId.set(target.packagingId)"
                      />
                      <span>{{ target.label }}</span>
                      <small class="muted">
                        {{ target.barcode ? 'remplace ' + target.barcode : 'aucun code' }}
                      </small>
                    </label>
                  }
                </div>
              } @else {
                <div class="muted">Chargement des unités…</div>
              }
            } @else {
              <div class="fld">
                <label for="uc-search">{{ rights.canEdit() ? 'Produit existant' : 'Nom du produit' }}</label>
                <input
                  #search
                  id="uc-search"
                  type="text"
                  autocomplete="off"
                  placeholder="Tapez au moins 2 lettres"
                  [ngModel]="words()"
                  (ngModelChange)="words.set($event)"
                />
              </div>
              <ul class="uc-results">
                @for (product of results(); track product.id) {
                  <li>
                    <button type="button" class="uc-result" (click)="choose(product)">
                      <span>{{ product.name }}</span>
                      <small class="muted">stock {{ formatAmount(product.stockQuantity, product.unit) }}</small>
                    </button>
                  </li>
                } @empty {
                  @if (words().trim().length >= minSearch) {
                    <li class="muted uc-none">Aucun produit ne correspond.</li>
                  }
                }
              </ul>
            }
          </div>

          <div class="modal-foot">
            @if (chosen(); as product) {
              <button class="btn ghost" type="button" (click)="back()">Retour</button>
              <button class="btn ghost" type="button" (click)="take(product)">Vendre sans associer</button>
              <button class="btn" type="button" [disabled]="busy() || !targets()" (click)="assign(product)">
                {{ busy() ? 'Enregistrement...' : 'Associer le code' }}
              </button>
            } @else {
              <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
              @if (rights.canEdit()) {
                <button class="btn" type="button" (click)="creating.set(true)">
                  ＋ Créer le produit avec ce code
                </button>
              }
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .uc-results {
      list-style: none;
      margin: 8px 0 0;
      padding: 0;
      max-height: 280px;
      overflow: auto;
    }

    .uc-result,
    .uc-target {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      color: inherit;
      font: inherit;
      text-align: left;
      padding: 9px 12px;
      margin-bottom: 6px;
      cursor: pointer;

      &:hover {
        border-color: var(--brand);
        background: var(--brand-soft);
      }
    }

    .uc-target {
      justify-content: flex-start;

      small {
        margin-left: auto;
      }
    }

    .uc-lead {
      margin: 0 0 10px;
    }

    .uc-none {
      padding: 6px 2px;
      font-size: 12.5px;
    }
  `,
})
export class UnknownCodeDialogComponent {
  private readonly api = inject(CatalogApi);
  private readonly assignment = inject(BarcodeAssignment);
  private readonly toasts = inject(ToastService);
  protected readonly rights = inject(ProductRights);

  readonly code = input.required<string>();
  /** The code now belongs to a product: enter it again, it will be found. */
  readonly assigned = output<string>();
  /** The article was found by its name, the code left aside. */
  readonly picked = output<Product>();
  readonly closed = output<void>();

  protected readonly minSearch = MIN_SEARCH;
  protected readonly words = signal('');
  protected readonly chosen = signal<Product | null>(null);
  protected readonly targets = signal<CodeTarget[] | null>(null);
  protected readonly targetId = signal<number | null>(null);
  protected readonly creating = signal(false);
  protected readonly busy = signal(false);
  protected readonly formatAmount = formatAmount;

  private readonly search = viewChild<ElementRef<HTMLInputElement>>('search');

  protected readonly results = toSignal(
    toObservable(this.words).pipe(
      debounceTime(200),
      switchMap((words) =>
        words.trim().length < MIN_SEARCH
          ? of([])
          : this.api.searchProducts(words, null, RESULTS).pipe(catchError(() => of([]))),
      ),
    ),
    { initialValue: [] as Product[] },
  );

  constructor() {
    afterNextRender(() => this.search()?.nativeElement.focus());
  }

  /** A seller takes it at once; a catalogue writer is first asked where the code goes. */
  protected choose(product: Product): void {
    if (!this.rights.canEdit()) {
      this.take(product);
      return;
    }
    this.chosen.set(product);
    this.targets.set(null);
    this.targetId.set(null);
    this.api.productUnits(product.id).subscribe((units) => this.targets.set(codeTargetsOf(units)));
  }

  protected take(product: Product): void {
    this.picked.emit(product);
    this.closed.emit();
  }

  protected back(): void {
    this.chosen.set(null);
    setTimeout(() => this.search()?.nativeElement.focus());
  }

  protected assign(product: Product): void {
    const target = this.targets()?.find((candidate) => candidate.packagingId === this.targetId());
    if (!target || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.assignment.assign(product.id, target.packagingId, this.code()).subscribe({
      next: () => {
        this.toasts.show(`Code ${this.code()} associé à ${product.name} (${target.label}).`);
        this.assigned.emit(this.code());
        this.closed.emit();
      },
      // Taken by another product, most often: the error toast says which.
      error: () => this.busy.set(false),
    });
  }

  protected onCreated(): void {
    this.assigned.emit(this.code());
    this.closed.emit();
  }

  /** The creation form has its own Escape; this one only closes what is on screen. */
  protected onEscape(): void {
    if (!this.creating()) {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
