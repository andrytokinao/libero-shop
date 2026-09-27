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
 * <p>Only the products that need it are listed. A line topping up an existing reference keeps that
 * reference's rayon, and a line whose file named a rayon already has one — so what is left is
 * exactly the new products whose file had no category column, or an empty cell in it. On a file
 * exported with a rayon column this dialog never opens at all.
 *
 * <p>Asked here rather than left blank on purpose. A product in no rayon is invisible in every
 * filtered list in the application, including the ones the depot uses to count the stock, and
 * nothing later would remind anybody. Asking once, as the products arrive, is the only moment at
 * which the answer is cheap.
 *
 * <p><b>Two panes, not fifty drop-downs.</b> Products on the left with a box each, the rayon tree
 * on the right with a box each: tick the products, tick a rayon, they are filed and the left
 * clears for the next batch. The honest alternative — one select per row — is the same number of
 * clicks for two products and ten times as many for eighty, because a file of eighty lands in
 * three or four rayons and the left pane lets each of those be one click. The tree stays visible
 * throughout, which also makes it obvious when the rayon wanted does not exist yet; creating it is
 * in the same pane.
 */
@Component({
  selector: 'app-category-assign-dialog',
  standalone: true,
  imports: [FormsModule, CategoryPickerComponent],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal widest" role="dialog" aria-modal="true" aria-labelledby="assign-title">
        <div class="modal-head">
          <div>
            <h2 id="assign-title">Catégorie des nouveaux produits</h2>
            <div class="sub muted">
              Cochez des produits à gauche, puis la catégorie à droite. Un produit sans catégorie
              n'apparaît dans aucune liste filtrée par rayon.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (lastAssigned(); as done) {
            <div class="asg-done">{{ done }}</div>
          }

          <div class="asg-panes">
            <!-- ------------------------------------------------ left: the products -->
            <section class="asg-pane">
              <header class="asg-pane-head">
                <label class="asg-all">
                  <input
                    type="checkbox"
                    [checked]="allPicked()"
                    [indeterminate]="somePicked() && !allPicked()"
                    (change)="pickAll($event)"
                  />
                  <span>
                    @if (picked().size) {
                      {{ picked().size }} produit(s) coché(s) sur {{ rows.length }}
                    } @else {
                      Aucun produit coché sur {{ rows.length }}
                    }
                  </span>
                </label>
                @if (waitingCount()) {
                  <button class="btn small ghost" type="button" (click)="pickWaiting()">
                    Cocher les {{ waitingCount() }} sans catégorie
                  </button>
                }
              </header>

              <ul class="asg-list">
                @for (row of rows; track row.line) {
                  <li [class.on]="picked().has(row.line)" [class.done]="!!chosenFor(row.line)">
                    <label>
                      <input
                        type="checkbox"
                        [checked]="picked().has(row.line)"
                        (change)="pick(row.line, $event)"
                      />
                      <span class="asg-name">{{ row.name }}</span>
                      <span class="asg-qty muted">{{ row.quantity }} {{ row.unit ?? '' }}</span>
                    </label>
                    @if (chosenFor(row.line); as categoryId) {
                      <button
                        class="asg-tag"
                        type="button"
                        title="Retirer cette catégorie"
                        (click)="clear(row.line)"
                      >
                        {{ pathOf(categoryId) }} ✕
                      </button>
                    } @else {
                      <span class="asg-tag empty">sans catégorie</span>
                    }
                  </li>
                }
              </ul>
            </section>

            <!-- ---------------------------------------------- right: the rayon tree -->
            <section class="asg-pane">
              <header class="asg-pane-head">
                <span class="asg-pane-title">Catégories</span>
                @if (!creating()) {
                  <button class="btn small ghost" type="button" (click)="creating.set(true)">
                    ＋ Nouvelle
                  </button>
                }
              </header>

              @if (creating()) {
                <div class="asg-new">
                  <div class="fld">
                    <label for="asg-new-name">Nom</label>
                    <input
                      id="asg-new-name"
                      type="text"
                      autocomplete="off"
                      maxlength="60"
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
              }

              @if (categories.length) {
                <ul class="asg-list asg-tree">
                  @for (node of categories; track node.id) {
                    <li [class.on]="lastUsed() === node.id">
                      <label [attr.title]="node.path">
                        <input
                          type="checkbox"
                          [checked]="lastUsed() === node.id"
                          [disabled]="!picked().size"
                          (change)="assign(node)"
                        />
                        <span class="asg-indent" [style.width.px]="node.depth * 16"></span>
                        @if (node.depth) {
                          <span class="muted">└</span>
                        }
                        <span class="asg-name" [class.asg-root]="!node.depth">{{ node.name }}</span>
                      </label>
                      <span class="asg-tag empty">{{ countIn(node.id) || '' }}</span>
                    </li>
                  }
                </ul>
                @if (!picked().size) {
                  <div class="asg-hint">Cochez d'abord un ou plusieurs produits à gauche.</div>
                }
              } @else {
                <div class="empty">
                  Aucune catégorie n'existe encore. Créez-en une ci-dessus, ou importez les produits
                  sans catégorie et rangez-les plus tard.
                </div>
              }
            </section>
          </div>
        </div>

        <div class="modal-foot">
          <div class="asg-foot muted">
            @if (waitingCount()) {
              {{ waitingCount() }} produit(s) encore sans catégorie.
            } @else {
              Tous les produits ont une catégorie.
            }
          </div>
          <button class="btn ghost" type="button" (click)="closed.emit()">Retour</button>
          <button
            class="btn ghost"
            type="button"
            [disabled]="busy || !waitingCount()"
            title="Les produits restants seront enregistrés sans catégorie"
            (click)="confirmed.emit(assignments())"
          >
            Importer sans catégorie
          </button>
          <button
            class="btn"
            type="button"
            [disabled]="busy || !!waitingCount()"
            (click)="confirmed.emit(assignments())"
          >
            {{ busy ? 'Enregistrement...' : 'Importer' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .asg-done {
      background: var(--brand-soft);
      color: var(--brand-dark);
      border-radius: 7px;
      padding: 8px 11px;
      font-size: 12.5px;
      margin-bottom: 12px;
    }

    /* Side by side above the breakpoint, stacked below it — a phone cannot hold two lists
       wide enough to read, and stacking keeps both usable with a thumb. */
    .asg-panes {
      display: flex;
      gap: 14px;

      @media (max-width: 900px) {
        flex-direction: column;
      }
    }

    .asg-pane {
      flex: 1;
      min-width: 0;
      border: 1px solid var(--line);
      border-radius: 9px;
      overflow: hidden;
    }

    .asg-pane-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      padding: 9px 11px;
      background: var(--bg);
      border-bottom: 1px solid var(--line);
      font-size: 12.5px;
    }

    .asg-pane-title {
      font-weight: 700;
    }

    .asg-all {
      display: flex;
      align-items: center;
      gap: 7px;
      font-weight: 600;
      cursor: pointer;
    }

    .asg-list {
      list-style: none;
      margin: 0;
      padding: 0;
      /* Both panes scroll on their own so the footer buttons never leave the screen. */
      max-height: 46vh;
      overflow-y: auto;

      li {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 6px 11px;
        border-bottom: 1px solid var(--line);
        font-size: 12.5px;

        &:last-child {
          border-bottom: none;
        }

        &.on {
          background: var(--brand-soft);
        }

        /* A product already filed reads as settled without disappearing: it can be re-filed. */
        &.done .asg-name {
          color: var(--ink-soft);
        }
      }

      label {
        display: flex;
        align-items: center;
        gap: 7px;
        flex: 1;
        min-width: 0;
        cursor: pointer;
      }

      input {
        margin: 0;
        flex: 0 0 auto;
        cursor: pointer;
      }
    }

    .asg-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .asg-root {
      font-weight: 600;
    }

    .asg-qty {
      flex: 0 0 auto;
      font-size: 11.5px;
    }

    .asg-indent {
      display: inline-block;
      flex: 0 0 auto;
    }

    .asg-tag {
      flex: 0 0 auto;
      max-width: 45%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 11px;
      font-weight: 600;
      border: none;
      border-radius: 20px;
      padding: 3px 9px;
      background: var(--brand-soft);
      color: var(--brand-dark);
      font-family: inherit;
      cursor: pointer;

      &.empty {
        background: none;
        color: var(--amber);
        cursor: default;
      }
    }

    .asg-hint {
      padding: 9px 11px;
      font-size: 11.5px;
      color: var(--ink-soft);
      border-top: 1px solid var(--line);
    }

    .asg-new {
      display: flex;
      gap: 10px;
      align-items: end;
      flex-wrap: wrap;
      padding: 11px;
      border-bottom: 1px solid var(--line);

      .fld {
        flex: 1;
        min-width: 150px;
        margin-bottom: 0;
      }

      .asg-new-actions {
        display: flex;
        gap: 7px;
      }
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
   * Line number to chosen rayon. A `Map` rather than an object so that "not answered" is
   * `undefined` in the type system too, not only at runtime.
   */
  private readonly chosen = signal<ReadonlyMap<number, number>>(new Map());
  /** Which products the next rayon tick will file. */
  protected readonly picked = signal<ReadonlySet<number>>(new Set());
  /** The rayon last used, kept ticked as a reminder of where the previous batch went. */
  protected readonly lastUsed = signal<number | null>(null);
  protected readonly lastAssigned = signal<string | null>(null);

  protected readonly creating = signal(false);
  protected readonly newName = signal('');
  protected readonly newParent = signal<number | null>(null);

  protected readonly waitingCount = computed(() => {
    const chosen = this.chosen();
    return this.rows.filter((row) => !chosen.has(row.line)).length;
  });

  protected readonly allPicked = computed(
    () => this.rows.length > 0 && this.picked().size === this.rows.length,
  );
  protected readonly somePicked = computed(() => this.picked().size > 0);

  /**
   * Rayons already at the last allowed level, which therefore cannot take a child. Greyed in the
   * parent picker rather than hidden: a disappearing option reads as a bug, a greyed one reads as
   * "not that one".
   */
  protected readonly tooDeepToHoldAChild = computed(() =>
    this.categories.filter((node) => node.depth >= MAX_CATEGORY_DEPTH - 1).map((node) => node.id),
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

  protected pathOf(categoryId: number): string {
    return this.categories.find((node) => node.id === categoryId)?.name ?? '—';
  }

  /** How many of the listed products are already filed in this rayon. */
  protected countIn(categoryId: number): number {
    const chosen = this.chosen();
    return this.rows.filter((row) => chosen.get(row.line) === categoryId).length;
  }

  // --------------------------------------------------------------------- picking

  protected pick(line: number, event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    this.picked.update((picked) => {
      const next = new Set(picked);
      if (on) {
        next.add(line);
      } else {
        next.delete(line);
      }
      return next;
    });
  }

  protected pickAll(event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    this.picked.set(on ? new Set(this.rows.map((row) => row.line)) : new Set());
  }

  /** The common next move once a batch has been filed: take everything still waiting. */
  protected pickWaiting(): void {
    const chosen = this.chosen();
    this.picked.set(
      new Set(this.rows.filter((row) => !chosen.has(row.line)).map((row) => row.line)),
    );
  }

  // -------------------------------------------------------------------- assigning

  /**
   * Files every ticked product in this rayon, then clears the left pane.
   *
   * <p>Clearing is what makes the two panes a loop rather than a form: the products just filed
   * stop being in the way, and the next tick starts a new batch. Their rayon stays visible on
   * each row, and the tag is a button, so a mistake costs one click to undo.
   */
  protected assign(node: CategoryNode): void {
    const batch = this.picked();
    if (!batch.size) {
      return;
    }
    this.chosen.update((chosen) => {
      const next = new Map(chosen);
      batch.forEach((line) => next.set(line, node.id));
      return next;
    });
    this.lastUsed.set(node.id);
    this.lastAssigned.set(`${batch.size} produit(s) rangé(s) dans « ${node.path} ».`);
    this.picked.set(new Set());
  }

  protected clear(line: number): void {
    this.chosen.update((chosen) => {
      const next = new Map(chosen);
      next.delete(line);
      return next;
    });
    this.lastAssigned.set(null);
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
