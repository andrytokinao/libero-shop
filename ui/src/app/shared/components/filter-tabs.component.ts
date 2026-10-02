import { Component, ElementRef, input, model, viewChildren } from '@angular/core';
import { IconName } from '../../core/ui/icons';
import { IconComponent } from './icon.component';

/** One choice of a {@link FilterTabsComponent}. */
export interface FilterTab<T> {
  readonly value: T;
  /** One or two words: "À remettre", "Tous". */
  readonly label: string;
  /** Drawn before the label, like the sidebar's. */
  readonly icon?: IconName;
  /** A count in a badge after the label; nothing is shown for 0, null or undefined. */
  readonly count?: number | null;
}

/**
 * A few mutually exclusive views of one list, as tabs across its top — where a drop-down hid
 * the choice behind a click and said nothing of what each view holds.
 *
 * <p>Each tab may carry a count, so "À remettre 3" tells the storekeeper there is work waiting
 * before they look. Generic in its value, so any screen's filter fits: a boolean, an enum, a
 * string. The value is a two-way `model`: `[(value)]="filter"`.
 *
 * <p>Keyboard as the WAI-ARIA tabs pattern: the selected tab takes the focus, arrows move and
 * select, Home and End jump to the ends.
 */
@Component({
  selector: 'app-filter-tabs',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="tabs" role="tablist" [attr.aria-label]="label()">
      @for (tab of tabs(); track tab.value; let i = $index) {
        <button
          #tabButton
          type="button"
          role="tab"
          class="tab"
          [class.active]="tab.value === value()"
          [attr.aria-selected]="tab.value === value()"
          [attr.tabindex]="tab.value === value() ? 0 : -1"
          (click)="value.set(tab.value)"
          (keydown)="onKey($event, i)"
        >
          @if (tab.icon) {
            <app-icon [name]="tab.icon" [size]="17" />
          }
          <span>{{ tab.label }}</span>
          @if (tab.count) {
            <span class="count" [attr.aria-label]="tab.count + ' ' + tab.label">{{ tab.count }}</span>
          }
        </button>
      }
    </div>
  `,
  styles: `
    .tabs {
      display: flex;
      gap: 4px;
      border-bottom: 1px solid var(--line);
      margin-bottom: 14px;
      /* Scrolls sideways on a narrow phone; never vertically, which only drew arrows. */
      overflow-x: auto;
      overflow-y: hidden;
    }

    .tab {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      border: none;
      border-bottom: 3px solid transparent;
      background: none;
      color: var(--ink-soft);
      font: inherit;
      font-weight: 600;
      white-space: nowrap;
      cursor: pointer;

      &:hover {
        color: var(--ink);
      }

      &:focus-visible {
        outline: 2px solid var(--brand);
        outline-offset: -2px;
        border-radius: 6px 6px 0 0;
      }

      &.active {
        color: var(--brand-dark);
        border-bottom-color: var(--brand);
      }
    }

    .count {
      min-width: 22px;
      height: 22px;
      padding: 0 7px;
      border-radius: 11px;
      background: var(--brand);
      color: var(--on-brand);
      font-size: 12px;
      line-height: 22px;
      text-align: center;
    }

    .tab:not(.active) .count {
      background: var(--amber);
    }
  `,
})
export class FilterTabsComponent<T> {
  readonly tabs = input.required<readonly FilterTab<T>[]>();
  /** The view shown — two-way. */
  readonly value = model.required<T>();
  /** What the tabs choose, for a screen reader: "Commandes". */
  readonly label = input('Filtre');

  private readonly buttons = viewChildren<ElementRef<HTMLButtonElement>>('tabButton');

  protected onKey(event: KeyboardEvent, index: number): void {
    const last = this.tabs().length - 1;
    const target =
      event.key === 'ArrowRight' ? (index === last ? 0 : index + 1)
      : event.key === 'ArrowLeft' ? (index === 0 ? last : index - 1)
      : event.key === 'Home' ? 0
      : event.key === 'End' ? last
      : null;
    if (target === null) {
      return;
    }
    event.preventDefault();
    this.value.set(this.tabs()[target].value);
    this.buttons()[target]?.nativeElement.focus();
  }
}
