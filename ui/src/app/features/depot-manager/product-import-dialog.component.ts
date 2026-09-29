import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CategoryNode,
  ImportAction,
  ImportOutcome,
  ProductImportLine,
  ProductImportPreview,
  Supplier,
  UNIT_SUGGESTIONS,
} from '../../core/models';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** How many lines a page shows. A screen's worth, so the ticking stays a scan and not a scroll. */
const PAGE_SIZE = 25;

/** Which lines the table is showing. */
type Lens = 'all' | 'create' | 'merge' | 'skipped' | 'problem';

/**
 * One line of the file as the operator is editing it.
 *
 * <p>Kept apart from the `ProductImportLine` the server sent: every field here is editable, and
 * the original is what "Fusionner" and "Nom du fichier" restore to. `outcome` is not carried
 * over — it is derived from `action` on every render, so a row that is renamed reads as renamed
 * the instant the button is pressed rather than after a round trip.
 */
export interface ProductImportRow {
  line: number;
  name: string;
  quantity: number;
  unit: string | null;
  price: number | null;
  /** Optional, and editable on a merge too: it describes these goods, not the product. */
  cost: number | null;
  barcode: string | null;
  categoryId: number | null;
  categoryPath: string;
  action: ImportAction;
  existing: ProductImportLine['existing'];
  matchedOn: string | null;
  suggestedName: string | null;
  selected: boolean;
  notes: string[];
  /** Refused by the reader itself — no name, or folded into another line. Cannot be ticked. */
  unusable: boolean;
  /** The name as the file wrote it, so a rename is reversible. */
  fileName: string;
}

/**
 * The first of the two import dialogs: what the file says, and what it would do.
 *
 * <p>Everything here is reversible, and that is the point of the screen existing. An import is
 * the one operation in this application that can wreck a catalogue in a single click — three
 * hundred duplicate references, a stock doubled because yesterday's file was loaded again — and
 * the only real defence is showing the consequence before it happens. So the dialog is
 * deliberately not a progress bar: it is a table the operator reads.
 *
 * <p>Three decisions are theirs alone and are therefore per row, never global. Whether a line is
 * imported at all (the box). Whether a line that matches an existing product tops up that
 * product's stock or becomes a second reference — "Lait 1L" from two dairies really are two
 * products, and no rule can tell. And the price, which a stock-count export very often has no
 * column for at all.
 *
 * <p>Ticks survive paging and filtering, because they live in the row objects rather than in the
 * rendered page. That matters: the natural way to use this on a 300-line file is to filter down
 * to the merges, untick two of them, switch to the creations, and apply the lot.
 */
@Component({
  selector: 'app-product-import-dialog',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal widest" role="dialog" aria-modal="true" aria-labelledby="import-title">
        <div class="modal-head">
          <div>
            <h2 id="import-title">Importer des produits — vérification</h2>
            <div class="sub muted">
              {{ head.total }} ligne(s) lues{{ head.separator ? ', séparateur « ' + head.separator + ' »' : ' (fichier Excel)' }}.
              Rien n'est encore enregistré.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <!-- What the reader made of the header line. Shown before the rows because a column
               read wrongly makes every row below it wrong in the same way. -->
          <div class="imp-cols">
            @if (head.recognised.length) {
              <div class="imp-col-line">
                <strong>Colonnes reconnues</strong> — {{ head.recognised.join(' · ') }}
              </div>
            }
            @if (head.missing.length) {
              <div class="imp-col-line warn">
                <strong>Colonnes absentes</strong> — {{ head.missing.join(', ') }}. Les valeurs
                correspondantes sont à saisir ici.
              </div>
            }
            @if (head.ignored.length) {
              <div class="imp-col-line warn">
                <strong>En-têtes ignorés</strong> — {{ head.ignored.join(', ') }}. Si l'un
                d'eux était une colonne attendue, corrigez son nom dans le fichier et
                recommencez.
              </div>
            }
            @if (head.createdRayons.length) {
              <div class="imp-col-line">
                <strong>Catégories qui seront créées</strong> —
                {{ head.createdRayons.join(' · ') }}
              </div>
            }
          </div>

          <div class="imp-tally">
            <span class="badge green">{{ createCount() }} à créer</span>
            <span class="badge amber">{{ mergeCount() }} à compléter</span>
            <span class="badge grey">{{ untickedCount() }} non cochées</span>
            @if (problemCount()) {
              <span class="badge red">{{ problemCount() }} à corriger</span>
            }
          </div>

          <div class="imp-bar">
            <label class="imp-all">
              <input
                type="checkbox"
                [checked]="allShownTicked()"
                [indeterminate]="someShownTicked() && !allShownTicked()"
                [disabled]="!usableShown().length"
                (change)="tickAllShown($event)"
              />
              <span>Tout cocher ({{ usableShown().length }} ligne(s) affichée(s))</span>
            </label>

            <div class="imp-spacer"></div>

            <label class="imp-lens" for="imp-lens">Afficher</label>
            <select id="imp-lens" class="field" [ngModel]="lens()" (ngModelChange)="setLens($event)">
              <option value="all">Toutes les lignes ({{ rows().length }})</option>
              <option value="create">Nouveaux produits ({{ toCreateCount() }})</option>
              <option value="merge">Produits existants ({{ toMergeCount() }})</option>
              <option value="problem">À corriger ({{ problemCount() }})</option>
              <option value="skipped">Lignes ignorées ({{ unusableCount() }})</option>
            </select>
          </div>

          @if (shown().length) {
            <div class="table-scroll">
              <table class="imp-table">
                <thead>
                  <tr>
                    <th class="col-tick"></th>
                    <th class="num col-line">Ligne</th>
                    <th>Produit</th>
                    <th class="num col-qty">Quantité</th>
                    <th class="col-unit">Unité</th>
                    <th class="num col-price">Prix</th>
                    <th class="num col-price" title="Prix d'achat unitaire, facultatif">
                      Prix d'achat
                    </th>
                    <th>Catégorie</th>
                    <th>État</th>
                    <th class="col-actions"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of page(); track row.line) {
                    <tr [class.off]="!row.selected" [class.bad]="!!problemOf(row)">
                      <td class="col-tick">
                        <input
                          type="checkbox"
                          [attr.aria-label]="'Importer la ligne ' + row.line"
                          [checked]="row.selected"
                          [disabled]="row.unusable"
                          (change)="tick(row, $event)"
                        />
                      </td>
                      <td class="num col-line muted">{{ row.line }}</td>
                      <td>
                        <input
                          type="text"
                          class="imp-name"
                          [attr.aria-label]="'Nom du produit, ligne ' + row.line"
                          [ngModel]="row.name"
                          (ngModelChange)="patch(row, { name: $event })"
                        />
                        @if (row.barcode) {
                          <div class="imp-sub muted">code-barres {{ row.barcode }}</div>
                        }
                      </td>
                      <td class="num col-qty">
                        <input
                          type="number"
                          min="0"
                          step="1"
                          [attr.aria-label]="'Quantité, ligne ' + row.line"
                          [ngModel]="row.quantity"
                          (ngModelChange)="patch(row, { quantity: asCount($event) })"
                        />
                      </td>
                      <td class="col-unit">
                        <input
                          type="text"
                          list="imp-units"
                          maxlength="16"
                          placeholder="—"
                          [attr.aria-label]="'Unité, ligne ' + row.line"
                          [ngModel]="row.unit"
                          (ngModelChange)="patch(row, { unit: $event })"
                        />
                      </td>
                      <td class="num col-price">
                        @if (row.action === merge) {
                          <span class="muted" title="Le prix du catalogue est conservé">
                            {{ row.existing?.price ?? 0 | ariary }}
                          </span>
                        } @else {
                          <input
                            type="number"
                            min="0"
                            step="1"
                            placeholder="à saisir"
                            [attr.aria-label]="'Prix, ligne ' + row.line"
                            [ngModel]="row.price"
                            (ngModelChange)="patch(row, { price: asAmount($event) })"
                          />
                        }
                      </td>
                      <td class="num col-price">
                        <input
                          type="number"
                          min="0"
                          step="1"
                          placeholder="—"
                          [attr.aria-label]="'Prix d’achat, ligne ' + row.line"
                          [ngModel]="row.cost"
                          (ngModelChange)="patch(row, { cost: asAmount($event) })"
                        />
                      </td>
                      <td>
                        @if (row.action === merge) {
                          <span class="muted">{{ row.existing?.categoryPath ?? '—' }}</span>
                        } @else if (row.categoryId !== null) {
                          {{ pathOf(row.categoryId) }}
                        } @else if (row.categoryPath) {
                          <span title="Cette catégorie sera créée">{{ row.categoryPath }} ＋</span>
                        } @else {
                          <span class="imp-todo">à choisir</span>
                        }
                      </td>
                      <td>
                        <span [class]="'badge ' + stateOf(row).tone">
                          {{ stateOf(row).label }}
                        </span>
                        @if (row.existing) {
                          <div class="imp-sub muted">
                            @if (row.action === merge) {
                              {{ row.existing.stockQuantity }} + {{ row.quantity }} =
                              {{ row.existing.stockQuantity + row.quantity }}
                            } @else {
                              distinct de « {{ row.existing.name }} »
                            }
                          </div>
                        }
                      </td>
                      <td class="col-actions">
                        @if (row.existing && row.action === merge) {
                          <button
                            class="btn small ghost"
                            type="button"
                            title="Créer une référence distincte au lieu de compléter celle-ci"
                            (click)="rename(row)"
                          >
                            Renommer
                          </button>
                        } @else if (row.existing) {
                          <button
                            class="btn small ghost"
                            type="button"
                            title="Ajouter la quantité au produit existant"
                            (click)="mergeBack(row)"
                          >
                            Fusionner
                          </button>
                        }
                      </td>
                    </tr>
                    @if (problemOf(row); as problem) {
                      <tr class="imp-note bad">
                        <td></td>
                        <td colspan="9">{{ problem }}</td>
                      </tr>
                    } @else if (row.notes.length) {
                      <tr class="imp-note">
                        <td></td>
                        <td colspan="9">{{ row.notes.join(' ') }}</td>
                      </tr>
                    }
                  }
                </tbody>
              </table>
            </div>

            <datalist id="imp-units">
              @for (unit of units; track unit) {
                <option [value]="unit"></option>
              }
            </datalist>

            @if (pageCount() > 1) {
              <div class="pager">
                <button
                  class="btn small ghost"
                  type="button"
                  [disabled]="page1() === 1"
                  (click)="goTo(page1() - 1)"
                >
                  ‹ Précédent
                </button>
                <span class="pager-at">Page {{ page1() }} sur {{ pageCount() }}</span>
                <button
                  class="btn small ghost"
                  type="button"
                  [disabled]="page1() === pageCount()"
                  (click)="goTo(page1() + 1)"
                >
                  Suivant ›
                </button>
              </div>
            }
          } @else {
            <div class="empty">Aucune ligne dans cette vue.</div>
          }
        </div>

        <div class="modal-foot imp-foot">
          <!-- Asked once for the whole file, because a file is one delivery or one count — never
               a mixture. Optional: leaving it blank is an inventory, which is the honest answer
               for a catalogue export and is what the note beside it says. -->
          <div class="imp-supplier">
            <label for="imp-supplier">Fournisseur</label>
            <select
              id="imp-supplier"
              class="field"
              [ngModel]="supplierId"
              (ngModelChange)="supplierIdChange.emit($event)"
            >
              <option [ngValue]="null">Aucun — inventaire</option>
              @for (supplier of suppliers; track supplier.id) {
                <option [ngValue]="supplier.id">{{ supplier.name }}</option>
              }
            </select>
          </div>

          <div class="imp-foot-note muted">
            @if (blocker(); as message) {
              {{ message }}
            } @else if (supplierId === null) {
              Chaque ligne laissera une entrée de stock à votre nom, sans fournisseur.
            } @else {
              Chaque ligne laissera une entrée de stock à votre nom, au nom de ce fournisseur.
            }
          </div>
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="button" [disabled]="busy || !!blocker()" (click)="submit()">
            {{ nextLabel() }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .imp-cols {
      font-size: 12px;
      color: var(--ink-soft);
      background: var(--bg);
      border-radius: 8px;
      padding: 9px 11px;
      margin-bottom: 12px;

      .imp-col-line + .imp-col-line {
        margin-top: 5px;
      }

      .warn {
        color: var(--amber);
      }
    }

    .imp-tally {
      display: flex;
      gap: 7px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }

    .imp-bar {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
      margin-bottom: 10px;

      .imp-spacer {
        flex: 1;
      }
    }

    .imp-all {
      display: flex;
      align-items: center;
      gap: 7px;
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
    }

    .imp-lens {
      font-size: 11.5px;
      color: var(--ink-soft);
      font-weight: 600;
    }

    /* The inputs live in the cells, so they must not carry the default field margins and
       must shrink to the column rather than forcing it wider. */
    .imp-table {
      input[type='text'],
      input[type='number'] {
        width: 100%;
        padding: 5px 7px;
        font-size: 12.5px;
      }

      td {
        padding: 7px 8px;
      }

      .col-tick {
        width: 1%;
      }

      .col-line {
        width: 1%;
        white-space: nowrap;
      }

      .col-qty,
      .col-price {
        width: 96px;
      }

      .col-unit {
        width: 92px;
      }

      .col-actions {
        width: 1%;
        white-space: nowrap;
      }

      /* An unticked row stays legible — it can be ticked again — but must not read as
         part of what is about to be imported. */
      tr.off > td {
        opacity: 0.5;
      }

      tr.bad > td {
        background: var(--red-soft);
      }
    }

    .imp-sub {
      font-size: 11px;
      margin-top: 3px;
    }

    .imp-todo {
      color: var(--amber);
      font-size: 12px;
      font-weight: 600;
    }

    /* Notes sit under their row rather than in a column: they are sentences, and a column
       wide enough for a sentence would squeeze the eight that carry the data. */
    .imp-note td {
      border-bottom: 1px solid var(--line);
      padding-top: 0;
      font-size: 11.5px;
      color: var(--ink-soft);
    }

    .imp-note.bad td {
      color: var(--red);
      font-weight: 600;
    }

    /* The supplier belongs in the footer rather than at the top: it is part of deciding to
       apply, not part of reading the file. */
    .imp-foot {
      flex-wrap: wrap;
      align-items: center;
    }

    .imp-supplier {
      display: flex;
      align-items: center;
      gap: 7px;

      label {
        font-size: 11.5px;
        color: var(--ink-soft);
        font-weight: 600;
      }
    }

    .imp-foot-note {
      flex: 1;
      min-width: 220px;
      font-size: 11.5px;
      text-align: left;
      align-self: center;
    }
  `,
})
export class ProductImportDialogComponent {
  /**
   * What the server made of the file. Setting it seeds the editable rows, so a second file
   * simply replaces the contents of an open dialog.
   */
  @Input({ required: true })
  set preview(preview: ProductImportPreview) {
    this.head = preview;
    this.rows.set(preview.lines.map((line) => ProductImportDialogComponent.toRow(line)));
    this.pageIndex.set(0);
    this.lens.set('all');
  }

  /** The header summary, read straight by the template; the rows live in a signal. */
  protected head!: ProductImportPreview;

  /** The rayon tree, for naming the category a line already resolved to. */
  @Input({ required: true }) categories: readonly CategoryNode[] = [];
  /** Who may have delivered this file's goods. Empty until a supplier has been recorded. */
  @Input() suppliers: readonly Supplier[] = [];
  /** Bound two-way by the parent, which owns it until the write. */
  @Input() supplierId: number | null = null;
  @Output() readonly supplierIdChange = new EventEmitter<number | null>();
  @Input() busy = false;
  /** The ticked lines, as edited. The parent decides whether a rayon still has to be asked for. */
  @Output() readonly confirmed = new EventEmitter<readonly ProductImportRow[]>();
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly units = UNIT_SUGGESTIONS;
  protected readonly merge = ImportAction.MERGE;

  protected readonly rows = signal<ProductImportRow[]>([]);
  protected readonly lens = signal<Lens>('all');
  /** Zero-based; `page1()` is what the operator is shown. */
  private readonly pageIndex = signal(0);

  private static toRow(line: ProductImportLine): ProductImportRow {
    const unusable = line.outcome === ImportOutcome.SKIPPED;
    return {
      line: line.line,
      name: line.outcome === ImportOutcome.RENAMED && line.suggestedName
        ? line.suggestedName
        : line.name,
      quantity: line.quantity,
      unit: line.unit,
      price: line.price,
      cost: line.cost,
      barcode: line.barcode,
      categoryId: line.categoryId,
      categoryPath: line.categoryPath,
      action: line.action,
      existing: line.existing,
      matchedOn: line.matchedOn,
      suggestedName: line.suggestedName,
      selected: line.selected && !unusable,
      notes: line.notes,
      unusable,
      fileName: line.name,
    };
  }

  // ------------------------------------------------------------------- the counts

  protected readonly createCount = computed(
    () => this.ticked().filter((row) => row.action === ImportAction.CREATE).length,
  );
  protected readonly mergeCount = computed(
    () => this.ticked().filter((row) => row.action === ImportAction.MERGE).length,
  );
  protected readonly untickedCount = computed(
    () => this.rows().filter((row) => !row.selected && !row.unusable).length,
  );
  protected readonly unusableCount = computed(
    () => this.rows().filter((row) => row.unusable).length,
  );
  protected readonly problemCount = computed(
    () => this.ticked().filter((row) => this.problemOf(row) !== null).length,
  );

  /** Usable lines by action, whether ticked or not — what the view filter counts. */
  protected readonly toCreateCount = computed(
    () =>
      this.rows().filter((row) => !row.unusable && row.action === ImportAction.CREATE).length,
  );
  protected readonly toMergeCount = computed(
    () =>
      this.rows().filter((row) => !row.unusable && row.action === ImportAction.MERGE).length,
  );

  private readonly ticked = computed(() => this.rows().filter((row) => row.selected));

  /** Ticked lines that will create a product and still have no rayon at all. */
  private readonly needCategory = computed(() =>
    this.ticked().filter(
      (row) =>
        row.action === ImportAction.CREATE && row.categoryId === null && !row.categoryPath,
    ),
  );

  // ------------------------------------------------------------ what is on screen

  /**
   * The lines the current view shows.
   *
   * <p>The create and merge views filter on the *action*, not on the tick. If they filtered on
   * the tick, unticking a row would make it vanish from under the cursor and there would be no
   * way back to it — and narrowing to the merges in order to untick two of them is the main
   * reason the filter exists. The counts in the dropdown are action counts for the same reason;
   * the badges above, which describe what the import will do, count ticks.
   */
  protected readonly shown = computed(() => {
    const rows = this.rows();
    switch (this.lens()) {
      case 'create':
        return rows.filter((row) => !row.unusable && row.action === ImportAction.CREATE);
      case 'merge':
        return rows.filter((row) => !row.unusable && row.action === ImportAction.MERGE);
      case 'skipped':
        return rows.filter((row) => row.unusable);
      case 'problem':
        return rows.filter((row) => row.selected && this.problemOf(row) !== null);
      default:
        return rows;
    }
  });

  protected readonly usableShown = computed(() => this.shown().filter((row) => !row.unusable));
  protected readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.shown().length / PAGE_SIZE)),
  );
  protected readonly page1 = computed(() => Math.min(this.pageIndex(), this.pageCount() - 1) + 1);
  protected readonly page = computed(() => {
    const start = (this.page1() - 1) * PAGE_SIZE;
    return this.shown().slice(start, start + PAGE_SIZE);
  });

  protected readonly allShownTicked = computed(
    () => this.usableShown().length > 0 && this.usableShown().every((row) => row.selected),
  );
  protected readonly someShownTicked = computed(() =>
    this.usableShown().some((row) => row.selected),
  );

  // ---------------------------------------------------------------- what is wrong

  /**
   * Why this row cannot be imported as it stands, or null.
   *
   * <p>One message, on the row, rather than a list at the bottom: the correction is made in the
   * cell next to it. A missing price is the common one by far — a stock-count export has a
   * quantity column and nothing else.
   */
  protected problemOf(row: ProductImportRow): string | null {
    if (!row.selected) {
      return null;
    }
    if (!row.name.trim()) {
      return 'Donnez un nom au produit, ou décochez la ligne.';
    }
    if (row.action === ImportAction.CREATE && (row.price === null || row.price < 0)) {
      return 'Saisissez le prix de vente : un nouveau produit ne peut pas être enregistré sans prix.';
    }
    if (row.quantity < 0) {
      return 'La quantité ne peut pas être négative.';
    }
    if (row.cost !== null && row.cost < 0) {
      return "Le prix d'achat ne peut pas être négatif : corrigez-le ou videz la case.";
    }
    return null;
  }

  /** Why the dialog cannot be submitted, or null. Shown in the footer, next to the button. */
  protected readonly blocker = computed<string | null>(() => {
    if (!this.ticked().length) {
      return 'Cochez au moins une ligne à importer.';
    }
    const problems = this.problemCount();
    if (problems) {
      return `${problems} ligne(s) cochée(s) sont incomplètes — voir la vue « À corriger ».`;
    }
    return null;
  });

  protected readonly nextLabel = computed(() => {
    if (this.busy) {
      return 'Enregistrement...';
    }
    const waiting = this.needCategory().length;
    return waiting ? `Continuer — ${waiting} catégorie(s) à choisir` : 'Importer';
  });

  // --------------------------------------------------------------------- editing

  protected patch(row: ProductImportRow, changes: Partial<ProductImportRow>): void {
    this.rows.update((rows) =>
      rows.map((candidate) => (candidate.line === row.line ? { ...candidate, ...changes } : candidate)),
    );
  }

  protected tick(row: ProductImportRow, event: Event): void {
    this.patch(row, { selected: (event.target as HTMLInputElement).checked });
  }

  /**
   * Ticks or unticks every line the current view shows — not just the current page. Paging
   * through twelve pages to tick a filtered set would make the filter useless, and the label
   * says how many lines are affected so nothing is silent.
   */
  protected tickAllShown(event: Event): void {
    const selected = (event.target as HTMLInputElement).checked;
    const affected = new Set(this.usableShown().map((row) => row.line));
    this.rows.update((rows) =>
      rows.map((row) => (affected.has(row.line) ? { ...row, selected } : row)),
    );
  }

  /** Turns a merge into a second reference, under the free name the server suggested. */
  protected rename(row: ProductImportRow): void {
    this.patch(row, {
      action: ImportAction.CREATE,
      name: row.suggestedName ?? row.name,
      // The price cell was showing the catalogue's, read-only, and is now an empty required
      // field. Seeding it from the look-alike gives a figure to correct rather than a blank to
      // find, which is the difference between two keystrokes and a trip to the shelf.
      price: row.price ?? row.existing?.price ?? null,
    });
  }

  /** Back to topping up the existing product, under the catalogue's own name. */
  protected mergeBack(row: ProductImportRow): void {
    this.patch(row, {
      action: ImportAction.MERGE,
      name: row.existing?.name ?? row.fileName,
    });
  }

  protected setLens(lens: Lens): void {
    this.lens.set(lens);
    this.pageIndex.set(0);
  }

  protected goTo(page1: number): void {
    this.pageIndex.set(Math.max(0, Math.min(page1, this.pageCount()) - 1));
  }

  // ---------------------------------------------------------------------- display

  protected stateOf(row: ProductImportRow): { label: string; tone: string } {
    if (row.unusable) {
      return { label: 'Ignorée', tone: 'grey' };
    }
    if (!row.selected) {
      return { label: 'Non cochée', tone: 'grey' };
    }
    if (row.action === ImportAction.MERGE) {
      return {
        label: row.matchedOn === 'barcode' ? 'Complète (code-barres)' : 'Complète (nom)',
        tone: 'amber',
      };
    }
    return row.existing ? { label: 'Nouveau, renommé', tone: 'green' } : { label: 'Nouveau', tone: 'green' };
  }

  protected pathOf(categoryId: number): string {
    return this.categories.find((node) => node.id === categoryId)?.path ?? '—';
  }

  /** An empty or unreadable number field is 0 units, never NaN in the payload. */
  protected asCount(value: unknown): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed) : 0;
  }

  /** An empty price stays null, which is what `problemOf` reports rather than guessing zero. */
  protected asAmount(value: unknown): number | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  protected submit(): void {
    if (this.busy || this.blocker()) {
      return;
    }
    this.confirmed.emit(this.ticked());
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}

/** The edited line, as the orchestrating component receives it. */
