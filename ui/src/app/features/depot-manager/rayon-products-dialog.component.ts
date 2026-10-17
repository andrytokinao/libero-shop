import { Component, ElementRef, OnInit, inject, input, output } from '@angular/core';
import { ApiResource, apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CodeMatch } from '../../core/catalog/product-pick';
import { CategoryNode, Product } from '../../core/models';
import { stockLabel } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';
import { ProductStore } from '../../core/store/product.store';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';
import { CategoryPickerComponent } from '../../shared/components/category-picker.component';
import { ProductPickerComponent } from '../../shared/components/product-picker.component';

/**
 * The products of one rayon, to see and to file: a search adds one to the rayon, and each line can
 * change rayon on the spot — where products used to be filed only from their own sheet or during
 * an import.
 *
 * <p>The rayon's sub-rayons are included, as on the sale screens' folder; each line says which
 * rayon it sits in exactly.
 */
@Component({
  selector: 'app-rayon-products-dialog',
  standalone: true,
  imports: [CategoryPickerComponent, ProductPickerComponent],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="rp-title">
        <div class="modal-head">
          <div>
            <h2 id="rp-title">Produits du rayon « {{ rayon().name }} »</h2>
            <div class="sub muted">Ses sous-rayons compris. Un produit change de rayon sans rien perdre d'autre.</div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          <div class="fld rp-add">
            <label for="rp-add">Ajouter un produit à ce rayon</label>
            <app-product-picker
              inputId="rp-add"
              [products]="allProducts()"
              (picked)="add($event)"
              (unknownCode)="toasts.show('Aucun produit ne porte le code ' + $event + '.')"
            />
          </div>

          @if (resource.value().length) {
            <div class="rp-list">
              @for (product of resource.value(); track product.id) {
                <div class="rp-row">
                  <div class="rp-name">
                    {{ product.name }}
                    <small class="muted">· stock {{ stockLabel(product) }}</small>
                  </div>
                  <div class="fld">
                    <label class="muted" [for]="'rp-move-' + product.id">Rayon</label>
                    <app-category-picker
                      [fieldId]="'rp-move-' + product.id"
                      [categories]="categories()"
                      [allowNone]="false"
                      [value]="product.category?.id ?? null"
                      (valueChange)="moveOne(product, $event)"
                    />
                  </div>
                </div>
              }
            </div>
          } @else if (!resource.loading()) {
            <div class="empty">Aucun produit dans ce rayon : cherchez-en un ci-dessus pour l'y ranger.</div>
          }
        </div>
        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .rp-add {
      margin-bottom: 14px;
    }

    .rp-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(180px, 260px);
      align-items: end;
      gap: 12px;
      padding: 8px 0;
      border-top: 1px solid var(--line);

      .fld {
        margin-bottom: 0;
        min-width: 0;
      }
    }

    .rp-name {
      align-self: center;
      font-weight: 600;
      min-width: 0;
    }

    @media (max-width: 600px) {
      .rp-row {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class RayonProductsDialogComponent implements OnInit {
  private readonly api = inject(CatalogApi);
  protected readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly rayon = input.required<CategoryNode>();
  readonly categories = input.required<readonly CategoryNode[]>();
  /** A product changed rayon: the counts of the rayons are out of date. */
  readonly changed = output<void>();
  readonly closed = output<void>();

  protected readonly allProducts = inject(ProductStore).list().value;
  /** Made once the rayon is known: a resource loads as soon as it exists. */
  protected resource!: ApiResource<Product[]>;
  protected readonly stockLabel = stockLabel;

  ngOnInit(): void {
    this.resource = apiResource<Product[]>([], () => this.api.products({ categoryId: this.rayon().id }));
  }

  protected add(match: CodeMatch): void {
    this.file(match.product, this.rayon().id, `« ${match.product.name} » rangé dans « ${this.rayon().name} ».`);
  }

  protected moveOne(product: Product, categoryId: number | null): void {
    const target = this.categories().find((node) => node.id === categoryId);
    if (!target || target.id === product.category?.id) {
      return;
    }
    this.file(product, target.id, `« ${product.name} » rangé dans « ${target.name} ».`);
  }

  private file(product: Product, categoryId: number, said: string): void {
    this.api.fileProducts(categoryId, [product.id]).subscribe(() => {
      this.toasts.show(said);
      this.resource.reload();
      this.changed.emit();
    });
  }

  protected onEscape(): void {
    if (isTopmostDialog(this.host.nativeElement)) {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
