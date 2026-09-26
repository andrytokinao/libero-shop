import {
  Component,
  ElementRef,
  EventEmitter,
  Output,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import {
  CategoryNode,
  CreateCategoryRequest,
  ProductImportAction,
  ProductImportLineRequest,
  ProductImportOutcome,
  ProductImportPreview,
  ProductImportResult,
  ProductImportResultLine,
} from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { TEXT_FILE_ACCEPT, readTextFile } from '../../core/utils/text-file.util';
import { CategoryAssignDialogComponent, CategoryAssignment } from './category-assign-dialog.component';
import {
  ProductImportDialogComponent,
  ProductImportRow,
} from './product-import-dialog.component';

/**
 * The whole import, from the button to the report.
 *
 * <p>Holds the flow rather than the tables: the file is read here, the preview is asked for here,
 * the two dialogs are opened in turn and the single write happens here. Splitting it this way is
 * what lets each dialog be a dumb table over rows it is handed — and it puts the one call that
 * changes the catalogue in one place, next to the comment saying so.
 *
 * <p>The second dialog is opened only when it has something to ask. A file exported with a rayon
 * column goes straight from the preview to the write, which is the common case and should not cost
 * a click.
 *
 * <p>Dropped in as a button by whichever screen owns the catalogue, and reports `imported` so that
 * screen can re-read its list.
 */
@Component({
  selector: 'app-product-import',
  standalone: true,
  imports: [ProductImportDialogComponent, CategoryAssignDialogComponent],
  template: `
    <button class="btn small" type="button" [disabled]="reading()" (click)="pick()">
      {{ reading() ? 'Lecture du fichier...' : '⇩ Importer des produits' }}
    </button>

    <!-- Outside the button so that re-rendering the label never resets the picker. -->
    <input
      #file
      type="file"
      class="imp-file"
      [accept]="accept"
      (change)="onFile($event)"
      aria-hidden="true"
      tabindex="-1"
    />

    @if (preview(); as loaded) {
      <app-product-import-dialog
        [preview]="loaded"
        [categories]="categories()"
        [busy]="saving()"
        (confirmed)="onPreviewConfirmed($event)"
        (closed)="reset()"
      />
    }

    @if (assigning(); as waiting) {
      <app-category-assign-dialog
        [rows]="waiting"
        [categories]="categories()"
        [busy]="saving()"
        (createCategory)="createCategory($event)"
        (confirmed)="onCategoriesChosen($event)"
        (closed)="assigning.set(null)"
      />
    }

    @if (result(); as report) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="imp-done">
          <div class="modal-head">
            <h2 id="imp-done">Import terminé</h2>
            <button class="x" type="button" aria-label="Fermer" (click)="finish()">✕</button>
          </div>
          <div class="modal-body">
            <ul class="imp-report">
              <li><strong>{{ report.created }}</strong> nouveau(x) produit(s) créé(s)</li>
              <li><strong>{{ report.merged }}</strong> produit(s) existant(s) complété(s)</li>
              <li><strong>{{ report.unitsAdded }}</strong> unité(s) ajoutée(s) au stock</li>
              @if (report.rayonsCreated.length) {
                <li>
                  Catégories créées : {{ report.rayonsCreated.join(' · ') }}
                </li>
              }
            </ul>

            @if (refused(report).length) {
              <div class="imp-refused">
                <strong>{{ report.skipped }} ligne(s) non importée(s)</strong>
                <ul>
                  @for (line of refused(report); track line.line) {
                    <li>Ligne {{ line.line }} — {{ line.name }} : {{ line.note }}</li>
                  }
                </ul>
              </div>
            }

            @if (renamed(report).length) {
              <div class="imp-renamed">
                <strong>{{ renamed(report).length }} produit(s) renommé(s)</strong>
                <ul>
                  @for (line of renamed(report); track line.line) {
                    <li>Ligne {{ line.line }} — {{ line.note }}</li>
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
    .imp-file {
      position: absolute;
      width: 1px;
      height: 1px;
      opacity: 0;
      pointer-events: none;
    }

    .imp-report {
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

    .imp-refused,
    .imp-renamed {
      margin-top: 14px;
      padding: 10px 12px;
      border-radius: 8px;
      font-size: 12px;

      ul {
        margin: 6px 0 0;
        padding-left: 18px;
      }
    }

    .imp-refused {
      background: var(--red-soft);
      color: var(--red);
    }

    .imp-renamed {
      background: var(--amber-soft);
      color: var(--amber);
    }
  `,
})
export class ProductImportComponent {
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);

  /** Raised once the catalogue has actually changed, so the host screen re-reads its list. */
  @Output() readonly imported = new EventEmitter<ProductImportResult>();

  protected readonly accept = TEXT_FILE_ACCEPT;

  private readonly categoryResource = apiResource<CategoryNode[]>([], () => this.api.categories());
  protected readonly categories = this.categoryResource.value;

  protected readonly reading = signal(false);
  protected readonly saving = signal(false);
  protected readonly preview = signal<ProductImportPreview | null>(null);
  /** The rows still waiting on a rayon; non-null exactly while the second dialog is open. */
  protected readonly assigning = signal<readonly ProductImportRow[] | null>(null);
  protected readonly result = signal<ProductImportResult | null>(null);

  /**
   * Everything the operator ticked, kept aside while the second dialog asks about a few of them.
   * The assignments come back keyed by line number, and this is what they are applied to.
   */
  private ticked: readonly ProductImportRow[] = [];

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

    this.api.importPreview(content).subscribe({
      next: (preview) => {
        this.reading.set(false);
        this.preview.set(preview);
      },
      // The error interceptor has already shown the server's own message, which for an import
      // is the one that says what is wrong with the file.
      error: () => this.reading.set(false),
    });
  }

  /** From the preview: either the rayons are all settled, or the second dialog has to ask. */
  protected onPreviewConfirmed(rows: readonly ProductImportRow[]): void {
    this.ticked = rows;
    const waiting = rows.filter(
      (row) =>
        row.action === ProductImportAction.CREATE && row.categoryId === null && !row.categoryPath,
    );
    if (waiting.length) {
      this.assigning.set(waiting);
      return;
    }
    this.write(rows);
  }

  protected onCategoriesChosen(assignments: readonly CategoryAssignment[]): void {
    const byLine = new Map(assignments.map((choice) => [choice.line, choice.categoryId]));
    this.write(
      this.ticked.map((row) =>
        byLine.has(row.line) ? { ...row, categoryId: byLine.get(row.line)! } : row,
      ),
    );
  }

  protected createCategory(request: CreateCategoryRequest): void {
    this.api.createCategory(request).subscribe({
      next: (created) => {
        // Re-read rather than push: the tree's order and depths are the server's to decide, and
        // the picker relies on that order to draw the indentation.
        this.categoryResource.reload();
        this.toasts.show(`Catégorie « ${created.path} » créée.`);
      },
    });
  }

  /** The one call that changes the catalogue. */
  private write(rows: readonly ProductImportRow[]): void {
    this.saving.set(true);
    this.api.importApply({ lines: rows.map(ProductImportComponent.toRequest) }).subscribe({
      next: (report) => {
        this.saving.set(false);
        this.preview.set(null);
        this.assigning.set(null);
        this.result.set(report);
        this.imported.emit(report);
      },
      error: () => this.saving.set(false),
    });
  }

  private static toRequest(row: ProductImportRow): ProductImportLineRequest {
    return {
      line: row.line,
      name: row.name.trim(),
      quantity: row.quantity,
      unit: row.unit?.trim() || null,
      // The dialog refuses to submit a merge-less row without a price, so the fallback is only
      // there to keep the payload well typed.
      price: row.action === ProductImportAction.MERGE ? (row.existing?.price ?? 0) : (row.price ?? 0),
      barcode: row.barcode?.trim() || null,
      categoryId: row.categoryId,
      categoryPath: row.categoryPath || null,
      action: row.action,
      mergeIntoId: row.action === ProductImportAction.MERGE ? (row.existing?.id ?? null) : null,
    };
  }

  /** Lines the server refused after the preview — a barcode taken in between, usually. */
  protected refused(report: ProductImportResult): ProductImportResultLine[] {
    return report.lines.filter((line) => line.outcome === ProductImportOutcome.SKIPPED);
  }

  /** Names the server had to free. Told plainly: the operator will look for the name they typed. */
  protected renamed(report: ProductImportResult): ProductImportResultLine[] {
    return report.lines.filter(
      (line) => line.outcome === ProductImportOutcome.RENAMED && !!line.note,
    );
  }

  protected reset(): void {
    this.preview.set(null);
    this.assigning.set(null);
    this.ticked = [];
  }

  protected finish(): void {
    this.result.set(null);
    this.reset();
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.finish();
    }
  }
}
