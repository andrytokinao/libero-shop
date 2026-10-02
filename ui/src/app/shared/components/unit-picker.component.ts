import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { Product } from '../../core/models';
import { Cart } from '../../core/sale/cart';
import { SaleUnit, formatQuantity, sameUnit, unitsOf } from '../../core/sale/sale-unit';
import { AriaryPipe } from '../pipes/ariary.pipe';

/** What a tap on a unit did: the unit, and how much went in — 0 when the shelf could not give one. */
export interface UnitPicked {
  readonly unit: SaleUnit;
  readonly added: number;
}

/**
 * The units of one product tile, as buttons: "kapoka", "kg", "sac 50 kg" — a tap puts one of
 * that unit in the basket — then the "−" that takes one back off.
 *
 * <p>A product sold in several units stays one tile on the sale screens; this is what opens
 * under it once tapped, so a cashier picks the rice first and how much of it second, without
 * three rice tiles crowding the grid. Each button shows what the basket already holds in that
 * unit, so the tile says "kg · 2" rather than leaving the cashier to look at the basket.
 *
 * <p>Shared by the till and the order-taking screen: one way to pick a unit, whichever screen
 * sells.
 */
@Component({
  selector: 'app-unit-picker',
  standalone: true,
  imports: [AriaryPipe],
  template: `
    <div class="up" role="group" [attr.aria-label]="'Unités de ' + product.name">
      @for (unit of units(); track unit.packagingId) {
        <button
          type="button"
          class="up-unit"
          [class.in]="countOf(unit) > 0"
          [disabled]="!fits(unit)"
          [attr.aria-label]="'Ajouter 1 ' + (unit.label ?? 'unité') + ' de ' + product.name"
          (click)="pick(unit)"
        >
          <span class="up-label">
            {{ unit.label ?? 'unité' }}
            @if (countOf(unit) > 0) {
              <span class="up-count">{{ formatQuantity(countOf(unit)) }}</span>
            }
          </span>
          <span class="up-price">{{ unit.price | ariary }}</span>
        </button>
      }
      @if (cart.quantityOf(product) > 0) {
        <button
          type="button"
          class="up-minus"
          [attr.aria-label]="'Retirer un ' + product.name"
          (click)="cart.change(product, -1)"
        >
          −
        </button>
      }
    </div>
  `,
  styles: `
    .up {
      display: flex;
      flex-wrap: wrap;
      align-items: stretch;
      gap: 5px;
      margin-top: 6px;
    }

    .up-unit {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 1px;
      padding: 5px 9px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      color: inherit;
      font: inherit;
      cursor: pointer;

      &:hover:not(:disabled) {
        border-color: var(--brand);
      }

      &.in {
        border-color: var(--brand);
        background: var(--brand-soft);
      }

      &:disabled {
        opacity: 0.45;
        cursor: not-allowed;
      }
    }

    .up-label {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 12px;
      font-weight: 600;
    }

    .up-count {
      min-width: 18px;
      padding: 0 5px;
      border-radius: 9px;
      background: var(--brand);
      color: var(--on-brand);
      font-size: 11px;
      text-align: center;
    }

    .up-price {
      font-size: 11px;
      color: var(--brand-dark);
    }

    .up-minus {
      margin-left: auto;
      align-self: center;
      width: 32px;
      height: 32px;
      border: 1px solid var(--brand);
      border-radius: 50%;
      background: var(--panel);
      color: var(--brand-dark);
      font: inherit;
      font-size: 18px;
      font-weight: 700;
      line-height: 1;
      cursor: pointer;

      &:hover {
        background: var(--brand-soft);
      }
    }
  `,
})
export class UnitPickerComponent {
  @Input({ required: true }) cart!: Cart;
  @Input({ required: true }) set product(product: Product) {
    this.current.set(product);
  }
  get product(): Product {
    return this.current()!;
  }
  /** After each tap, so the screen can refocus its field or say the shelf is short. */
  @Output() readonly picked = new EventEmitter<UnitPicked>();

  private readonly current = signal<Product | null>(null);
  protected readonly units = computed(() => {
    const product = this.current();
    return product ? unitsOf(product) : [];
  });
  protected readonly formatQuantity = formatQuantity;

  /** What the basket holds of this product in this unit. */
  protected countOf(unit: SaleUnit): number {
    return this.cart.linesOf(this.product.id).find((line) => sameUnit(line.unit, unit))?.quantity ?? 0;
  }

  /** Whether one more of this unit is still on the shelf: a sack needs 175 kapoka left, not one. */
  protected fits(unit: SaleUnit): boolean {
    return this.cart.remainingStock(this.product) >= unit.factor;
  }

  protected pick(unit: SaleUnit): void {
    this.picked.emit({ unit, added: this.cart.add(this.product, 1, unit) });
  }
}
