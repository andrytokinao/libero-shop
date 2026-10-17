import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

/** One entry of an {@link ActionMenuComponent}. */
export interface MenuAction<T extends string = string> {
  readonly id: T;
  readonly label: string;
  /** Shown in red: an entry that removes something. */
  readonly danger?: boolean;
  /** A small heading over the entries that share it, drawn where the group changes. */
  readonly group?: string;
  /** In grey on the right: what the entry does, or the value it changes. */
  readonly hint?: string;
}

/** Room one entry takes, to decide whether the panel opens under its button or above it. */
const ITEM_HEIGHT = 40;
const PANEL_PADDING = 12;
const GROUP_HEIGHT = 24;

/**
 * A "⋮" button opening a short list of actions — what a `mat-menu` is, in this application's own
 * styles, which has no component library.
 *
 * <p>The panel is placed on the viewport rather than inside its parent: the menus sit in table
 * rows and cards that scroll and clip, and a panel positioned inside one would be cut at its
 * edge. For the same reason it closes when anything scrolls — it would otherwise stay behind
 * while its row moved away.
 *
 * <p>Arrow keys move through the entries, Escape closes and hands the focus back to the button.
 */
@Component({
  selector: 'app-action-menu',
  standalone: true,
  host: {
    '(document:keydown.escape)': 'close(true)',
    '(window:resize)': 'close()',
  },
  template: `
    <button
      #trigger
      type="button"
      class="am-trigger"
      aria-haspopup="menu"
      [attr.aria-expanded]="open()"
      [attr.aria-label]="label()"
      [title]="label()"
      (click)="toggle($event)"
    >
      ⋮
    </button>
    @if (open()) {
      <div class="am-catch" (click)="close()"></div>
      <div
        #panel
        class="am-panel"
        role="menu"
        [attr.aria-label]="label()"
        [style.right.px]="place().right"
        [style.top.px]="place().top"
        [style.bottom.px]="place().bottom"
        (keydown)="onKey($event)"
      >
        @for (action of actions(); track action.id; let i = $index) {
          @if (action.group && action.group !== actions()[i - 1]?.group) {
            <div class="am-group" role="presentation">{{ action.group }}</div>
          }
          <button
            type="button"
            role="menuitem"
            class="am-item"
            [class.danger]="action.danger"
            (click)="pick(action)"
          >
            <span>{{ action.label }}</span>
            @if (action.hint) {
              <small class="am-hint">{{ action.hint }}</small>
            }
          </button>
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: inline-block;
    }

    .am-trigger {
      width: 30px;
      height: 30px;
      border: 1px solid transparent;
      border-radius: 8px;
      background: none;
      color: var(--ink-soft);
      font: inherit;
      font-size: 18px;
      font-weight: 700;
      line-height: 1;
      cursor: pointer;

      &:hover,
      &[aria-expanded='true'] {
        border-color: var(--line);
        background: var(--bg);
        color: var(--brand);
      }
    }

    .am-catch {
      position: fixed;
      inset: 0;
      z-index: 70;
    }

    .am-panel {
      position: fixed;
      z-index: 71;
      min-width: 190px;
      max-width: calc(100vw - 16px);
      /* A long menu on a short screen scrolls inside itself rather than leaving the screen. */
      max-height: calc(100vh - 16px);
      overflow-y: auto;
      padding: 6px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--panel);
      box-shadow: 0 10px 28px rgba(0, 0, 0, 0.18);
      display: flex;
      flex-direction: column;
    }

    .am-group {
      font-size: 10.5px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--ink-soft);
      padding: 8px 12px 2px;
    }

    .am-hint {
      margin-left: auto;
      padding-left: 16px;
      color: var(--ink-soft);
      font-size: 11.5px;
    }

    .am-item {
      display: flex;
      align-items: baseline;
      border: none;
      border-radius: 7px;
      background: none;
      color: inherit;
      font: inherit;
      font-size: 13px;
      text-align: left;
      padding: 10px 12px;
      cursor: pointer;
      white-space: nowrap;

      &:hover,
      &:focus-visible {
        background: var(--brand-soft);
        outline: none;
      }

      &.danger {
        color: var(--red);
      }
    }
  `,
})
export class ActionMenuComponent<T extends string = string> {
  readonly actions = input.required<readonly MenuAction<T>[]>();
  /** What the button says to a screen reader and on hover. */
  readonly label = input('Actions');
  readonly picked = output<T>();

  protected readonly open = signal(false);
  private readonly anchor = signal<DOMRect | null>(null);

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  /** Listened to while open only, and in the capture phase: a scroll does not bubble. */
  private readonly onScroll = (): void => this.close();

  constructor() {
    inject(DestroyRef).onDestroy(() => document.removeEventListener('scroll', this.onScroll, true));
  }

  /**
   * Right-aligned under the button; above it when the bottom of the screen is too close, which
   * is the last rows of a long table.
   */
  protected readonly place = computed(() => {
    const rect = this.anchor();
    if (!rect) {
      return { right: 0, top: 0, bottom: null };
    }
    const right = Math.max(8, window.innerWidth - rect.right);
    const groups = new Set(this.actions().map((action) => action.group).filter(Boolean)).size;
    const height = this.actions().length * ITEM_HEIGHT + groups * GROUP_HEIGHT + PANEL_PADDING;
    const fitsBelow = rect.bottom + 4 + height <= window.innerHeight || rect.top < height;
    return fitsBelow
      ? { right, top: rect.bottom + 4, bottom: null }
      : { right, top: null, bottom: window.innerHeight - rect.top + 4 };
  });

  protected toggle(event: Event): void {
    // The menu often sits on something clickable itself — a card, a row.
    event.stopPropagation();
    if (this.open()) {
      this.close();
      return;
    }
    this.anchor.set(this.trigger().nativeElement.getBoundingClientRect());
    this.open.set(true);
    document.addEventListener('scroll', this.onScroll, true);
    // Once the panel is drawn: the first entry takes the focus, so the arrows work at once.
    setTimeout(() => this.items()[0]?.focus());
  }

  /** @param refocus hand the focus back to the button — after Escape, not after a click away */
  protected close(refocus = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    document.removeEventListener('scroll', this.onScroll, true);
    if (refocus) {
      this.trigger().nativeElement.focus();
    }
  }

  protected pick(action: MenuAction<T>): void {
    this.close();
    this.picked.emit(action.id);
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }
    event.preventDefault();
    const items = this.items();
    const current = items.indexOf(document.activeElement as HTMLButtonElement);
    const step = event.key === 'ArrowDown' ? 1 : -1;
    items[(current + step + items.length) % items.length]?.focus();
  }

  private items(): HTMLButtonElement[] {
    return Array.from(this.panel()?.nativeElement.querySelectorAll<HTMLButtonElement>('.am-item') ?? []);
  }
}
