import { Component, Input, computed, signal } from '@angular/core';
import { Cart, CartLine } from '../../core/sale/cart';
import {
  SaleUnit,
  formatQuantity,
  parseQuantity,
  sameUnit,
  unitsOf,
} from '../../core/sale/sale-unit';

/**
 * The quantity of one basket line, and the unit it is sold in.
 *
 * <p>"−", the quantity, "+" as before; the quantity can also be typed — "0,5" for half a kilo —
 * because loose goods are weighed, not counted. Beside them, one button per unit the product sells
 * in ("kapoka", "kg", "sac 50 kg"): a tap sells the line in that unit, keeping the number. A product
 * sold in one unit only shows its name, or nothing.
 *
 * <p>Shared by the till and the order-taking screen, so a line is edited the same way on both.
 */
@Component({
  selector: 'app-cart-line-quantity',
  standalone: true,
  template: `
    <div class="cq">
      <span class="cq-qty">
        <button
          class="qtybtn"
          type="button"
          [attr.aria-label]="'Retirer un ' + (line.unit.label ?? '')"
          (click)="cart.changeLine(line, -1)"
        >
          −
        </button>
        <input
          class="cq-input"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          aria-label="Quantité"
          [class.invalid]="invalid()"
          [value]="formatQuantity(line.quantity)"
          (change)="typed($event)"
          (keydown.enter)="typed($event)"
        />
        <button
          class="qtybtn"
          type="button"
          [attr.aria-label]="'Ajouter un ' + (line.unit.label ?? '')"
          [disabled]="cart.remainingStock(line.product) <= 0"
          (click)="cart.changeLine(line, 1)"
        >
          +
        </button>
      </span>
      @if (units().length > 1) {
        <span class="cq-units" role="group" aria-label="Unité de vente">
          @for (unit of units(); track unit.packagingId) {
            <button
              type="button"
              class="cq-unit"
              [class.on]="isCurrent(unit)"
              [attr.aria-pressed]="isCurrent(unit)"
              (click)="cart.setLineUnit(line, unit)"
            >
              {{ unit.label ?? 'unité' }}
            </button>
          }
        </span>
      } @else if (line.unit.label) {
        <span class="cq-label muted">{{ line.unit.label }}</span>
      }
    </div>
  `,
  styles: `
    .cq {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px 8px;
    }

    .cq-qty {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .cq-input {
      width: 52px;
      padding: 3px 4px;
      text-align: center;
      font: inherit;

      &.invalid {
        border-color: var(--red);
      }
    }

    .cq-units {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 4px;
    }

    .cq-unit {
      border: 1px solid var(--line);
      border-radius: 20px;
      background: none;
      color: var(--ink-soft);
      font: inherit;
      font-size: 11.5px;
      padding: 2px 9px;
      cursor: pointer;

      &.on {
        border-color: var(--brand);
        background: var(--brand-soft);
        color: var(--brand-dark);
        font-weight: 600;
      }
    }

    .cq-label {
      font-size: 12px;
    }
  `,
})
export class CartLineQuantityComponent {
  @Input({ required: true }) cart!: Cart;
  @Input({ required: true }) set line(line: CartLine) {
    this.current.set(line);
    this.invalid.set(false);
  }
  get line(): CartLine {
    return this.current()!;
  }

  private readonly current = signal<CartLine | null>(null);
  /** The last thing typed could not be read; the field keeps the line's real quantity. */
  protected readonly invalid = signal(false);
  protected readonly units = computed(() => {
    const line = this.current();
    return line ? unitsOf(line.product) : [];
  });

  protected readonly formatQuantity = formatQuantity;

  protected isCurrent(unit: SaleUnit): boolean {
    return sameUnit(unit, this.line.unit);
  }

  /**
   * A typed quantity, applied on Enter or when the field is left. Returns nothing: Angular
   * cancels the key event of a handler that answers false, and that once swallowed every key
   * typed on the till.
   */
  protected typed(event: Event): void {
    const field = event.target as HTMLInputElement;
    const quantity = parseQuantity(field.value);
    if (quantity === null) {
      this.invalid.set(true);
      field.value = formatQuantity(this.line.quantity);
      return;
    }
    this.invalid.set(false);
    this.cart.setLineQuantity(this.line, quantity);
    // Capped by the stock, the line may hold less than typed: show what it really holds.
    field.value = formatQuantity(
      this.cart.lines().find((line) => line.product.id === this.line.product.id && sameUnit(line.unit, this.line.unit))
        ?.quantity ?? quantity,
    );
  }
}
