import { Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import { CodeMatch, MIN_PICK_LETTERS, findByCode, matchProducts } from '../../core/catalog/product-pick';
import { Product } from '../../core/models';
import { stockLabel } from '../../core/sale/sale-unit';
import { CameraScanButtonComponent } from './camera-scan-button.component';

/**
 * The "Produit" field of the stock forms: type two letters of the name, or scan the code — with
 * the scanner, which types it and presses Enter, or with the phone's camera. Each proposal shows
 * the stock there is now.
 *
 * <p>A code printed on a carton picks the product <em>and</em> the carton (`packagingId`), so the
 * receipt is counted in cartons without a second choice.
 */
@Component({
  selector: 'app-product-picker',
  standalone: true,
  imports: [CameraScanButtonComponent],
  template: `
    <div class="pp">
      <div class="pp-row">
        <input
          #field
          type="text"
          class="field"
          autocomplete="off"
          role="combobox"
          aria-autocomplete="list"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="inputId() + '-list'"
          [id]="inputId()"
          [placeholder]="placeholder"
          [value]="shown()"
          (input)="type($any($event.target).value)"
          (focus)="onFocus()"
          (click)="selectChosen()"
          (blur)="onBlur()"
          (keydown)="onKeydown($event)"
        />
        <app-camera-scan-button (scanned)="pickCode($event)" />
      </div>
      @if (open()) {
        <ul class="pp-list" role="listbox" [id]="inputId() + '-list'">
          @for (product of results(); track product.id; let i = $index) {
            <li
              role="option"
              class="pp-item"
              [class.active]="i === active()"
              [attr.aria-selected]="i === active()"
              (mousedown)="$event.preventDefault()"
              (click)="pick({ product, packagingId: null })"
            >
              <span>{{ product.name }}</span>
              <small [class.low]="product.lowStock">stock {{ stockLabel(product) }}</small>
            </li>
          } @empty {
            <li class="pp-none muted">Aucun produit ne porte ce nom ou ce code.</li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .pp {
      position: relative;
    }

    .pp-row {
      display: flex;
      gap: 6px;

      input {
        flex: 1;
        min-width: 0;
      }
    }

    .pp-list {
      position: absolute;
      z-index: 20;
      left: 0;
      right: 0;
      top: calc(100% + 4px);
      margin: 0;
      padding: 4px;
      list-style: none;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 9px;
      box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
      max-height: 300px;
      overflow-y: auto;
    }

    .pp-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      padding: 8px 10px;
      border-radius: 6px;
      cursor: pointer;

      &.active,
      &:hover {
        background: var(--brand-soft);
      }

      small {
        color: var(--ink-soft);
        white-space: nowrap;
      }

      small.low {
        color: var(--red);
        font-weight: 600;
      }
    }

    .pp-none {
      padding: 8px 10px;
      font-size: 13px;
    }
  `,
})
export class ProductPickerComponent {
  /** The catalogue to choose from — the screen's own list, kept current by the store. */
  readonly products = input.required<readonly Product[]>();
  /** The product chosen, shown in the field while nothing is being typed. */
  readonly selected = input<Product | undefined>(undefined);
  readonly inputId = input('product-picker');

  /** A product chosen — by its name, or by a code with the unit it is printed on. */
  readonly picked = output<CodeMatch>();
  /** A code no product carries: the screen says so (or offers to create it). */
  readonly unknownCode = output<string>();

  protected readonly placeholder = `Nom (${MIN_PICK_LETTERS} lettres) ou code-barres`;
  protected readonly stockLabel = stockLabel;

  /** What is typed; null while the field shows the chosen product. */
  private readonly typed = signal<string | null>(null);
  private readonly focused = signal(false);
  protected readonly active = signal(0);

  protected readonly shown = computed(() => this.typed() ?? this.selected()?.name ?? '');
  protected readonly results = computed(() => matchProducts(this.products(), this.typed() ?? ''));
  protected readonly open = computed(
    () => this.focused() && (this.typed() ?? '').trim().length >= MIN_PICK_LETTERS,
  );

  protected type(text: string): void {
    this.typed.set(text);
    this.active.set(0);
  }

  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  protected onFocus(): void {
    this.focused.set(true);
    this.selectChosen();
  }

  /**
   * The chosen name is selected whole, so what is typed or scanned next replaces it rather than
   * being added to it — the field keeps the focus after a choice, ready for the next code.
   */
  protected selectChosen(): void {
    const field = this.field().nativeElement;
    // Not after a camera scan: selecting would pull the focus back into the field.
    if (this.typed() === null && document.activeElement === field) {
      field.select();
    }
  }

  /** Leaving the field without choosing puts the chosen product back. */
  protected onBlur(): void {
    this.focused.set(false);
    this.typed.set(null);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const count = this.results().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.active.set(count ? (this.active() + 1) % count : 0);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.set(count ? (this.active() - 1 + count) % count : 0);
        break;
      case 'Escape':
        this.typed.set(null);
        break;
      case 'Enter':
        // Inside a form, Enter would send it with the wrong product.
        event.preventDefault();
        this.confirm();
        break;
    }
  }

  /** Enter: a whole code first — the scanner's way — else the proposal highlighted. */
  private confirm(): void {
    const text = (this.typed() ?? '').trim();
    if (!text) {
      return;
    }
    const byCode = findByCode(this.products(), text);
    if (byCode) {
      this.pick(byCode);
      return;
    }
    const proposal = this.results()[this.active()];
    if (proposal) {
      this.pick({ product: proposal, packagingId: null });
    } else if (/^\d{6,}$/.test(text)) {
      this.unknownCode.emit(text);
    }
  }

  protected pickCode(code: string): void {
    const match = findByCode(this.products(), code);
    if (match) {
      this.pick(match);
    } else {
      this.unknownCode.emit(code);
    }
  }

  protected pick(match: CodeMatch): void {
    this.typed.set(null);
    this.picked.emit(match);
    // Once the chosen name is on screen.
    setTimeout(() => this.selectChosen());
  }
}
