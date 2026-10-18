import { Component, ElementRef, inject, input, signal } from '@angular/core';
import { Shortcut, isEnabled } from '../../core/sale/shortcuts';

/**
 * "⌨": the screen's function keys, on demand. Read from the very list the keys are handled by,
 * so the legend never promises a key that does nothing. A key that cannot act now — F4 with an
 * empty basket — is greyed.
 *
 * <p>Hidden on a phone held in the hand, which has no function keys to press.
 */
@Component({
  selector: 'app-shortcut-legend',
  standalone: true,
  host: {
    '(document:keydown.escape)': 'open.set(false)',
    '(document:click)': 'onDocumentClick($event)',
  },
  template: `
    <button
      class="sl-trigger"
      type="button"
      title="Raccourcis clavier"
      aria-label="Raccourcis clavier"
      [attr.aria-expanded]="open()"
      (click)="open.set(!open())"
    >
      ⌨
    </button>
    @if (open()) {
      <div class="sl-panel" role="dialog" aria-label="Raccourcis clavier">
        <div class="sl-title">Raccourcis clavier</div>
        <ul>
          @for (shortcut of shortcuts(); track shortcut.key) {
            <li [class.off]="!isEnabled(shortcut)">
              <kbd>{{ shortcut.key }}</kbd>
              <span>{{ shortcut.label }}</span>
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-block;
    }

    .sl-trigger {
      height: 100%;
      min-height: 36px;
      padding: 0 10px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      color: var(--ink-soft);
      font-size: 18px;
      cursor: pointer;

      &:hover,
      &[aria-expanded='true'] {
        color: var(--brand-ink);
        border-color: var(--brand);
      }
    }

    .sl-panel {
      position: absolute;
      right: 0;
      top: calc(100% + 6px);
      z-index: 50;
      min-width: 240px;
      padding: 10px 12px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--panel);
      box-shadow: 0 8px 24px rgb(0 0 0 / 12%);

      ul {
        list-style: none;
        margin: 0;
        padding: 0;
      }

      li {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 4px 0;
        font-size: 13.5px;
      }

      li.off {
        opacity: 0.45;
      }
    }

    .sl-title {
      font-weight: 700;
      font-size: 13px;
      margin-bottom: 6px;
    }

    kbd {
      min-width: 36px;
      text-align: center;
      padding: 2px 6px;
      border: 1px solid var(--line);
      border-bottom-width: 2px;
      border-radius: 5px;
      font: 600 12px Consolas, monospace;
    }

    @media (pointer: coarse) and (max-width: 600px) {
      :host {
        display: none;
      }
    }
  `,
})
export class ShortcutLegendComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly shortcuts = input.required<readonly Shortcut[]>();
  protected readonly open = signal(false);
  protected readonly isEnabled = isEnabled;

  /** A click anywhere else closes it, like any small menu. */
  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
