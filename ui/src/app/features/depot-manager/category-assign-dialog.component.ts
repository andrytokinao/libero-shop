import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CategoryNode, CreateCategoryRequest, MAX_CATEGORY_DEPTH } from '../../core/models';
import { CategoryPickerComponent } from '../../shared/components/category-picker.component';
import { ProductImportRow } from './product-import-dialog.component';

/** What the operator chose for one product waiting on a rayon. */
export interface CategoryAssignment {
  line: number;
  categoryId: number;
}

/**
 * The second import dialog: the rayon of each product that has none.
 *
 * <p>Only the products that need it are listed. A line topping up an existing reference keeps
 * that reference's rayon, and a line whose file named a rayon already has one — so what is left
 * is exactly the new products whose file had no category column, or an empty cell in it. On a
 * file exported with a rayon column this dialog never opens at all.
 *
 * <p>Asked here rather than left blank on purpose. A product in no rayon is invisible in every
 * filtered list in the application, including the ones the depot uses to count the stock, and
 * nothing later would remind anybody. Asking once, at the moment the products arrive, is the only
 * point at which the answer is cheap.
 *
 * <p>Two shortcuts, because the honest version of this dialog is fifty selects. "Appliquer à
 * toutes les lignes" fills the empty ones from the row being set — most of a file lands in two
 * or three rayons — and a rayon absent from the tree can be created here without leaving, since
 * discovering it is missing halfway through is exactly when it is needed.
 */
@Component({
  selector: 'app-category-assign-dialog',
  standalone: true,
  imports: [FormsModule, CategoryPickerComponent],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="assign-title">
        <div class="modal-head">
          <div>
            <h2 id="assign-title">Catégorie des nouveaux produits</h2>
            <div class="sub muted">
              {{ rows.length }} produit(s) sans catégorie. Un produit sans catégorie n'apparaît
              dans aucune liste filtrée par rayon.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="asg-tools">
            <div class="fld">
              <label for="asg-bulk">Remplir les lignes vides avec</label>
              <app-category-picker
                fieldId="asg-bulk"
                [categories]="categories"
                [allowNone]="true"
                noneLabel="—"
                [value]="bulk()"
                (valueChange)="bulk.set($event)"
              />
            </div>
            <button
              class="btn small ghost"
              type="button"
              [disabled]="bulk() === null || !emptyCount()"
              (click)="applyToEmpty()"
            >
              Appliquer aux {{ emptyCount() }} restantes
            </button>
          </div>

          @if (creating()) {
            <div class="asg-new">
              <div class="fld">
                <label for="asg-new-name">Nouvelle catégorie</label>
                <input
                  id="asg-new-name"
                  type="text"
                  autocomplete="off"
                  placeholder="Boissons fraîches"
                  [ngModel]="newName()"
                  (ngModelChange)="newName.set($event)"
                />
              </div>
              <div class="fld">
                <label for="asg-new-parent">À l'intérieur de</label>
                <app-category-picker
                  fieldId="asg-new-parent"
                  [categories]="categories"
                  [allowNone]="true"
                  noneLabel="Aucune — catégorie principale"
                  [blocked]="tooDeepToHoldAChild()"
                  [value]="newParent()"
                  (valueChange)="newParent.set($event)"
                />
              </div>
              <div class="asg-new-actions">
                <button
                  class="btn small"
                  type="button"
                  [disabled]="busy || !newName().trim()"
                  (click)="emitCreate()"
                >
                  Créer
                </button>
                <button class="btn small ghost" type="button" (click)="creating.set(false)">
                  Annuler
                </button>
              </div>
            </div>
          } @else {
            <button class="btn small ghost" type="button" (click)="creating.set(true)">
              ＋ Créer une catégorie
            </button>
          }

          <table style="margin-top:14px;">
            <thead>
              <tr>
                <th class="num col-line">Ligne</th>
                <th>Produit</th>
                <th class="num">Quantité</th>
                <th class="col-pick">Catégorie</th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows; track row.line) {
                <tr [class.bad]="chosenFor(row.line) === null">
                  <td class="num col-line muted">{{ row.line }}</td>
                  <td>{{ row.name }}</td>
                  <td class="num">{{ row.quantity }} {{ row.unit ?? '' }}</td>
                  <td class="col-pick">
                    <app-category-picker
                      [categories]="categories"
                      [allowNone]="true"
                      noneLabel="— à choisir"
                      [value]="chosenFor(row.line)"
                      (valueChange)="choose(row.line, $event)"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="modal-foot">
          <div class="asg-foot muted">
            @if (emptyCount()) {
              {{ emptyCount() }} produit(s) sans catégorie.
            } @else {
              Toutes les lignes ont une catégorie.
            }
          </div>
          <button class="btn ghost" type="button" (click)="closed.emit()">Retour</button>
          <button
            class="btn ghost"
            type="button"
            [disabled]="busy || !emptyCount()"
            title="Les produits restants seront enregistrés sans catégorie"
            (click)="confirmed.emit(assignments())"
          >
            Importer sans catégorie
          </button>
          <button class="btn" type="button" [disabled]="busy || !!emptyCount()" (click)="confirmed.emit(assignments())">
            {{ busy ? 'Enregistrement...' : 'Importer' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .asg-tools {
      display: flex;
      gap: 10px;
      align-items: end;
      flex-wrap: wrap;
      padding-bottom: 12px;
      margin-bottom: 12px;
      border-bottom: 1px solid var(--line);

      .fld {
        display: flex;
        flex-direction: column;
        gap: 5px;
        min-width: 220px;

        label {
          font-size: 11.5px;
          color: var(--ink-soft);
          font-weight: 600;
        }
      }
    }

    .asg-new {
      display: flex;
      gap: 10px;
      align-items: end;
      flex-wrap: wrap;
      background: var(--bg);
      border-radius: 8px;
      padding: 11px;

      .fld {
        display: flex;
        flex-direction: column;
        gap: 5px;
        min-width: 200px;

        label {
          font-size: 11.5px;
          color: var(--ink-soft);
          font-weight: 600;
        }
      }

      .asg-new-actions {
        display: flex;
        gap: 7px;
      }
    }

    .col-line {
      width: 1%;
      white-space: nowrap;
    }

    .col-pick {
      width: 230px;
    }

    /* A row still waiting is tinted rather than badged: the whole dialog is the complaint. */
    tr.bad td {
      background: var(--amber-soft);
    }

    .asg-foot {
      flex: 1;
      font-size: 11.5px;
      text-align: left;
      align-self: center;
    }
  `,
})
export class CategoryAssignDialogComponent {
  /** The ticked lines that will create a product and have no rayon. */
  @Input({ required: true }) rows: readonly ProductImportRow[] = [];
  @Input({ required: true }) categories: readonly CategoryNode[] = [];
  @Input() busy = false;
  @Output() readonly confirmed = new EventEmitter<readonly CategoryAssignment[]>();
  /** Asks the parent to create the rayon; the parent reloads the tree and it appears here. */
  @Output() readonly createCategory = new EventEmitter<CreateCategoryRequest>();
  @Output() readonly closed = new EventEmitter<void>();

  /**
   * Line number to chosen rayon. A line absent from here has not been answered — a `Map` rather
   * than an object so that "absent" is `undefined` in the type system too, not just at runtime.
   */
  protected readonly chosen = signal<ReadonlyMap<number, number>>(new Map());
  protected readonly bulk = signal<number | null>(null);

  protected readonly creating = signal(false);
  protected readonly newName = signal('');
  protected readonly newParent = signal<number | null>(null);

  protected readonly emptyCount = computed(() => {
    const chosen = this.chosen();
    return this.rows.filter((row) => !chosen.has(row.line)).length;
  });

  /**
   * Rayons already at the last allowed level, which therefore cannot take a child. Greyed in the
   * parent picker rather than hidden, so the cap reads as a rule and not as a missing row.
   */
  protected readonly tooDeepToHoldAChild = computed(() =>
    this.categories
      .filter((node) => node.depth >= MAX_CATEGORY_DEPTH - 1)
      .map((node) => node.id),
  );

  protected readonly assignments = computed<CategoryAssignment[]>(() => {
    const chosen = this.chosen();
    return this.rows.flatMap((row) => {
      const categoryId = chosen.get(row.line);
      return categoryId === undefined ? [] : [{ line: row.line, categoryId }];
    });
  });

  protected chosenFor(line: number): number | null {
    return this.chosen().get(line) ?? null;
  }

  protected choose(line: number, categoryId: number | null): void {
    this.chosen.update((chosen) => {
      const next = new Map(chosen);
      if (categoryId === null) {
        next.delete(line);
      } else {
        next.set(line, categoryId);
      }
      return next;
    });
  }

  /** Fills only the lines still unanswered, so a deliberate choice is never overwritten. */
  protected applyToEmpty(): void {
    const categoryId = this.bulk();
    if (categoryId === null) {
      return;
    }
    this.chosen.update((chosen) => {
      const next = new Map(chosen);
      for (const row of this.rows) {
        if (!next.has(row.line)) {
          next.set(row.line, categoryId);
        }
      }
      return next;
    });
  }

  protected emitCreate(): void {
    const name = this.newName().trim();
    if (!name) {
      return;
    }
    this.createCategory.emit({ name, parentId: this.newParent() });
    this.newName.set('');
    this.creating.set(false);
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
