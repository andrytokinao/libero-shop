import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { CatalogApi } from '../../core/api/catalog.api';
import {
  ImportOutcome,
  SimpleImportKind,
  SimpleImportLineRequest,
  SimpleImportPreview,
  SimpleImportResult,
  SimpleImportResultLine,
} from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { TEXT_FILE_ACCEPT, readTextFile } from '../../core/utils/text-file.util';
import { SimpleImportDialogComponent } from './simple-import-dialog.component';

/**
 * The button, the dialog and the report for a simple import, parameterised by `kind`.
 *
 * <p>The rayon and supplier screens each drop one of these in and listen for `imported`. Neither
 * knows how an import works, and this knows nothing about rayons or suppliers beyond the `kind`
 * it is handed — the server decides the columns, the wording travels as data.
 */
@Component({
  selector: 'app-simple-import',
  standalone: true,
  imports: [SimpleImportDialogComponent],
  template: `
    <button class="btn small ghost" type="button" [disabled]="reading()" (click)="pick()">
      {{ reading() ? 'Lecture du fichier...' : '⇩ ' + kind.action }}
    </button>

    <input
      #file
      type="file"
      class="simp-file"
      [accept]="accept"
      (change)="onFile($event)"
      aria-hidden="true"
      tabindex="-1"
    />

    @if (preview(); as loaded) {
      <app-simple-import-dialog
        [kind]="kind"
        [preview]="loaded"
        [busy]="saving()"
        (confirmed)="write($event)"
        (closed)="preview.set(null)"
      />
    }

    @if (result(); as report) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="simp-done">
          <div class="modal-head">
            <h2 id="simp-done">Import terminé</h2>
            <button class="x" type="button" aria-label="Fermer" (click)="finish()">✕</button>
          </div>
          <div class="modal-body">
            <ul class="simp-report">
              <li><strong>{{ report.created }}</strong> {{ kind.noun }} créée(s)</li>
              <li><strong>{{ report.merged }}</strong> déjà enregistrée(s)</li>
            </ul>
            @if (refused(report).length) {
              <div class="simp-refused">
                <strong>{{ report.skipped }} ligne(s) non importée(s)</strong>
                <ul>
                  @for (line of refused(report); track line.line) {
                    <li>Ligne {{ line.line }} — {{ line.label }} : {{ line.note }}</li>
                  }
                </ul>
              </div>
            }
          </div>
          <div class="modal-foot">
            <button class="btn" type="button" (click)="finish()">Fermer</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    /* Kept in the DOM and out of sight: a hidden input is still clickable from script, and a
       native file button cannot be styled to match the rest of the application. */
    .simp-file {
      position: absolute;
      width: 1px;
      height: 1px;
      opacity: 0;
      pointer-events: none;
    }

    .simp-report {
      list-style: none;
      margin: 0;
      padding: 0;
      font-size: 13px;

      li {
        padding: 5px 0;
        border-bottom: 1px solid var(--line);

        &:last-child {
          border-bottom: none;
        }
      }
    }

    .simp-refused {
      margin-top: 14px;
      padding: 10px 12px;
      border-radius: 8px;
      font-size: 12px;
      background: var(--red-soft);
      color: var(--red);

      ul {
        margin: 6px 0 0;
        padding-left: 18px;
      }
    }
  `,
})
export class SimpleImportComponent {
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);

  @Input({ required: true }) kind!: SimpleImportKind;
  /** Raised once the list has actually changed, so the host screen re-reads it. */
  @Output() readonly imported = new EventEmitter<SimpleImportResult>();

  protected readonly accept = TEXT_FILE_ACCEPT;
  protected readonly reading = signal(false);
  protected readonly saving = signal(false);
  protected readonly preview = signal<SimpleImportPreview | null>(null);
  protected readonly result = signal<SimpleImportResult | null>(null);

  private readonly filePicker = viewChild.required<ElementRef<HTMLInputElement>>('file');

  protected pick(): void {
    this.filePicker().nativeElement.click();
  }

  protected async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Cleared straight away so picking the same file twice in a row still fires a change.
    input.value = '';
    if (!file) {
      return;
    }

    this.reading.set(true);
    let content: string;
    try {
      content = await readTextFile(file);
    } catch (error) {
      this.reading.set(false);
      this.toasts.show(error instanceof Error ? error.message : 'Fichier illisible.');
      return;
    }

    this.api.simpleImportPreview(this.kind.resource, content).subscribe({
      next: (preview) => {
        this.reading.set(false);
        this.preview.set(preview);
      },
      // The error interceptor has already shown the server's message, which for an import is
      // the one that says what is wrong with the file.
      error: () => this.reading.set(false),
    });
  }

  /** The one call that changes anything. */
  protected write(lines: SimpleImportLineRequest[]): void {
    this.saving.set(true);
    this.api.simpleImportApply(this.kind.resource, { lines }).subscribe({
      next: (report) => {
        this.saving.set(false);
        this.preview.set(null);
        this.result.set(report);
        this.imported.emit(report);
      },
      error: () => this.saving.set(false),
    });
  }

  protected refused(report: SimpleImportResult): SimpleImportResultLine[] {
    return report.lines.filter((line) => line.outcome === ImportOutcome.SKIPPED);
  }

  protected finish(): void {
    this.result.set(null);
    this.preview.set(null);
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.finish();
    }
  }
}
