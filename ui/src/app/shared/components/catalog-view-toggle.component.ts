import { Component, inject } from '@angular/core';
import { CatalogView, CatalogViewService } from '../../core/services/catalog-view.service';
import { IconName } from '../../core/ui/icons';
import { IconComponent } from './icon.component';

interface ViewOption {
  readonly value: CatalogView;
  readonly label: string;
  readonly icon: IconName;
}

const OPTIONS: readonly ViewOption[] = [
  { value: 'grid', label: 'Tuiles', icon: 'tiles' },
  { value: 'list', label: 'Liste', icon: 'list' },
];

/**
 * Tiles or lines — two buttons beside a sale screen's search field, the current one pressed. The
 * choice applies at once, on every screen that sells, and is kept on this device only.
 */
@Component({
  selector: 'app-catalog-view-toggle',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="picker" role="radiogroup" aria-label="Affichage des produits">
      @for (option of options; track option.value) {
        <button
          type="button"
          role="radio"
          [class.on]="catalogView.view() === option.value"
          [attr.aria-checked]="catalogView.view() === option.value"
          [attr.aria-label]="option.label"
          [title]="'Affichage : ' + option.label"
          (click)="catalogView.choose(option.value)"
        >
          <app-icon [name]="option.icon" [size]="18" />
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .picker {
      display: inline-flex;
      padding: 3px;
      gap: 3px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--bg);
    }

    button {
      display: inline-flex;
      align-items: center;
      padding: 7px 9px;
      border: none;
      border-radius: 7px;
      background: none;
      color: var(--ink-soft);
      cursor: pointer;

      &.on {
        background: var(--panel);
        color: var(--ink);
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
      }
    }
  `,
})
export class CatalogViewToggleComponent {
  protected readonly catalogView = inject(CatalogViewService);
  protected readonly options = OPTIONS;
}
