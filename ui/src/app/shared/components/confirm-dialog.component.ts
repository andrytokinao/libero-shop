import { Component, ElementRef, afterNextRender, inject, input, output, viewChild } from '@angular/core';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';

/**
 * The application's own "are you sure?", in place of the browser's `window.confirm()`: the same
 * look as every other dialog, the amount or the consequence said in full, and a button that names
 * what it does ("Remettre 25 000 Ar") rather than "OK".
 *
 * <p>The cautious button has the focus: Enter on a dialog opened by mistake changes nothing.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="alertdialog" aria-modal="true" aria-labelledby="cf-title" aria-describedby="cf-text">
        <div class="modal-head">
          <div>
            <h2 id="cf-title">{{ title() }}</h2>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          <p id="cf-text" class="cf-text" [class.cf-danger]="danger()">{{ message() }}</p>
        </div>
        <div class="modal-foot">
          <button #cancel class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button
            class="btn"
            type="button"
            [class.cf-danger-btn]="danger()"
            [disabled]="busy()"
            (click)="confirmed.emit()"
          >
            {{ confirmLabel() }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .cf-text {
      margin: 0;
      font-size: 14px;
    }

    .cf-danger {
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--red-soft);
      color: var(--red);
    }

    .cf-danger-btn {
      background: var(--red);
      border-color: var(--red);
    }
  `,
})
export class ConfirmDialogComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input.required<string>();
  /** Something hard to undo: the text and the button in red. */
  readonly danger = input(false);
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly closed = output<void>();

  private readonly cancel = viewChild<ElementRef<HTMLButtonElement>>('cancel');

  constructor() {
    afterNextRender(() => this.cancel()?.nativeElement.focus());
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
