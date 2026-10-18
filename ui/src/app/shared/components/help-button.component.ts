import { Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { HELP_TEXTS, HelpTopic } from '../../core/help/help-texts';

/**
 * The "?" in a window's header: two or three sentences and an example from the shop, unfolded on
 * demand — no separate help page to look for.
 */
@Component({
  selector: 'app-help',
  standalone: true,
  host: { '(document:click)': 'onDocumentClick($event)' },
  template: `
    <button
      class="hb-trigger"
      type="button"
      title="Aide"
      aria-label="Aide sur cette fenêtre"
      [attr.aria-expanded]="open()"
      (click)="open.set(!open())"
    >
      ?
    </button>
    @if (open()) {
      <div class="hb-panel" role="note">
        <p>{{ help().text }}</p>
        <p class="hb-example"><strong>Exemple :</strong> {{ help().example }}</p>
      </div>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
      align-self: flex-start;
    }

    .hb-trigger {
      width: 28px;
      height: 28px;
      border: 1px solid var(--line);
      border-radius: 50%;
      background: var(--panel);
      color: var(--ink-soft);
      font-weight: 700;
      cursor: pointer;

      &:hover,
      &[aria-expanded='true'] {
        color: var(--brand-ink);
        border-color: var(--brand);
      }
    }

    .hb-panel {
      position: absolute;
      right: 0;
      top: calc(100% + 6px);
      z-index: 60;
      width: min(320px, 80vw);
      padding: 10px 12px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--panel);
      box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
      font-size: 13px;
      line-height: 1.5;
      text-align: left;

      p {
        margin: 0;
      }
    }

    .hb-example {
      margin-top: 6px !important;
      color: var(--ink-soft);
    }
  `,
})
export class HelpButtonComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly topic = input.required<HelpTopic>();
  protected readonly open = signal(false);
  protected readonly help = computed(() => HELP_TEXTS[this.topic()]);

  /** A click anywhere else folds it back. */
  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
