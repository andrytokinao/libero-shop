import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ImportAction,
  ImportOutcome,
  SimpleImportKind,
  SimpleImportLine,
  SimpleImportLineRequest,
  SimpleImportPreview,
} from '../../core/models';

/** A screen's worth of rows, so ticking stays a scan rather than a scroll. */
const PAGE_SIZE = 25;

/** Which lines the table is showing. */
type Lens = 'all' | 'create' | 'merge' | 'skipped';

/** One line as the operator is editing it. */
interface Row {
  line: number;
  values: Record<string, string>;
  action: ImportAction;
  existingId: number | null;
  existingLabel: string | null;
  selected: boolean;
  notes: string[];
  /** Refused by the reader itself — no name, or a duplicate of another line. Cannot be ticked. */
  unusable: boolean;
}

/**
 * The preview for an import that is only a few text columns wide.
 *
 * <p>One component, two resources — rayons and suppliers — because the server sends the shape of
 * the file along with its contents. The table renders a column per field spec, so this knows
 * nothing about categories or suppliers beyond the wording it is handed in `kind`. A third
 * resource of the same sort would need no new screen at all.
 *
 * <p>It is the same bargain the product dialog makes and for the same reason: an import is quick
 * to run and tedious to undo, so what it would do is shown as a table before it does it. What is
 * missing here on purpose is the product dialog's machinery — no merge-or-rename choice, no price,
 * no second dialog — because neither of these resources has a decision of that kind to make.
 */
@Component({
  selector: 'app-simple-import-dialog',
  standalone: true,
  imports: [FormsModule],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="simp-title">
        <div class="modal-head">
          <div>
            <h2 id="simp-title">{{ kind.title }}</h2>
            <div class="sub muted">
              {{ head.total }} ligne(s) lues, séparateur « {{ head.separator }} ».
              Rien n'est encore enregistré.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
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
                <strong>En-têtes ignorés</strong> — {{ head.ignored.join(', ') }}.
              </div>
            }
            <div class="imp-col-line">{{ kind.caution }}</div>
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

            <label class="imp-lens" for="simp-lens">Afficher</label>
            <select
              id="simp-lens"
              class="field"
              [ngModel]="lens()"
              (ngModelChange)="setLens($event)"
            >
              <option value="all">Toutes les lignes ({{ rows().length }})</option>
              <option value="create">À créer ({{ toCreateCount() }})</option>
              <option value="merge">Déjà enregistrées ({{ toMergeCount() }})</option>
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
                    @for (field of head.fields; track field.key) {
                      <th>{{ field.label }}{{ field.required ? ' *' : '' }}</th>
                    }
                    <th>État</th>
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
                      @for (field of head.fields; track field.key) {
                        <td>
                          <input
                            type="text"
                            [attr.aria-label]="field.label + ', ligne ' + row.line"
                            [attr.maxlength]="field.maxLength"
                            [ngModel]="row.values[field.key]"
                            (ngModelChange)="patchValue(row, field.key, $event)"
                          />
                        </td>
                      }
                      <td>
                        <span [class]="'badge ' + stateOf(row).tone">{{ stateOf(row).label }}</span>
                      </td>
                    </tr>
                    @if (problemOf(row); as problem) {
                      <tr class="imp-note bad">
                        <td></td>
                        <td [attr.colspan]="head.fields.length + 2">{{ problem }}</td>
                      </tr>
                    } @else if (row.notes.length) {
                      <tr class="imp-note">
                        <td></td>
                        <td [attr.colspan]="head.fields.length + 2">{{ row.notes.join(' ') }}</td>
                      </tr>
                    }
                  }
                </tbody>
              </table>
            </div>

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

        <div class="modal-foot">
          <div class="imp-foot-note muted">
            @if (blocker(); as message) {
              {{ message }}
            }
          </div>
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="button" [disabled]="busy || !!blocker()" (click)="submit()">
            {{ busy ? 'Enregistrement...' : 'Importer' }}
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

    .imp-table {
      input[type='text'] {
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

      tr.off > td {
        opacity: 0.5;
      }

      tr.bad > td {
        background: var(--red-soft);
      }
    }

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

    .imp-foot-note {
      flex: 1;
      font-size: 11.5px;
      text-align: left;
      align-self: center;
    }
  `,
})
export class SimpleImportDialogComponent {
  /** The wording: which resource this is, and what applying it will and will not do. */
  @Input({ required: true }) kind!: SimpleImportKind;

  /** Setting it seeds the editable rows, so a second file replaces an open dialog's contents. */
  @Input({ required: true })
  set preview(preview: SimpleImportPreview) {
    this.head = preview;
    this.rows.set(preview.lines.map(SimpleImportDialogComponent.toRow));
    this.pageIndex.set(0);
    this.lens.set('all');
  }

  /** The header summary, read straight by the template; the rows live in a signal. */
  protected head!: SimpleImportPreview;

  @Input() busy = false;
  @Output() readonly confirmed = new EventEmitter<SimpleImportLineRequest[]>();
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly rows = signal<Row[]>([]);
  protected readonly lens = signal<Lens>('all');
  private readonly pageIndex = signal(0);

  private static toRow(line: SimpleImportLine): Row {
    const unusable = line.outcome === ImportOutcome.SKIPPED;
    return {
      line: line.line,
      values: { ...line.values },
      action: line.action,
      existingId: line.existingId,
      existingLabel: line.existingLabel,
      selected: line.selected && !unusable,
      notes: line.notes,
      unusable,
    };
  }

  // ------------------------------------------------------------------- the counts

  private readonly ticked = computed(() => this.rows().filter((row) => row.selected));

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

  /** Usable lines by action, ticked or not — what the view filter counts. */
  protected readonly toCreateCount = computed(
    () => this.rows().filter((row) => !row.unusable && row.action === ImportAction.CREATE).length,
  );
  protected readonly toMergeCount = computed(
    () => this.rows().filter((row) => !row.unusable && row.action === ImportAction.MERGE).length,
  );

  // ------------------------------------------------------------ what is on screen

  /**
   * The create and merge views filter on the action, not on the tick: filtering on the tick would
   * make a row vanish from under the cursor as it was unticked, with no way back to it.
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

  /** Why this row cannot be imported as it stands, or null. Driven by the server's field specs. */
  protected problemOf(row: Row): string | null {
    if (!row.selected) {
      return null;
    }
    const missing = this.head.fields.find(
      (field) => field.required && !(row.values[field.key] ?? '').trim(),
    );
    return missing
      ? `« ${missing.label} » est obligatoire, ou décochez la ligne.`
      : null;
  }

  protected readonly blocker = computed<string | null>(() => {
    if (!this.ticked().length) {
      return 'Cochez au moins une ligne à importer.';
    }
    const problems = this.problemCount();
    return problems ? `${problems} ligne(s) cochée(s) sont incomplètes.` : null;
  });

  // --------------------------------------------------------------------- editing

  private patch(row: Row, changes: Partial<Row>): void {
    this.rows.update((rows) =>
      rows.map((candidate) =>
        candidate.line === row.line ? { ...candidate, ...changes } : candidate,
      ),
    );
  }

  protected patchValue(row: Row, key: string, value: string): void {
    this.patch(row, { values: { ...row.values, [key]: value } });
  }

  protected tick(row: Row, event: Event): void {
    this.patch(row, { selected: (event.target as HTMLInputElement).checked });
  }

  /** Every line the current view shows, not just the current page — the label says how many. */
  protected tickAllShown(event: Event): void {
    const selected = (event.target as HTMLInputElement).checked;
    const affected = new Set(this.usableShown().map((row) => row.line));
    this.rows.update((rows) =>
      rows.map((row) => (affected.has(row.line) ? { ...row, selected } : row)),
    );
  }

  protected setLens(lens: Lens): void {
    this.lens.set(lens);
    this.pageIndex.set(0);
  }

  protected goTo(page1: number): void {
    this.pageIndex.set(Math.max(0, Math.min(page1, this.pageCount()) - 1));
  }

  protected stateOf(row: Row): { label: string; tone: string } {
    if (row.unusable) {
      return { label: 'Ignorée', tone: 'grey' };
    }
    if (!row.selected) {
      return { label: 'Non cochée', tone: 'grey' };
    }
    return row.action === ImportAction.MERGE
      ? { label: 'Déjà enregistrée', tone: 'amber' }
      : { label: 'À créer', tone: 'green' };
  }

  protected submit(): void {
    if (this.busy || this.blocker()) {
      return;
    }
    this.confirmed.emit(
      this.ticked().map((row) => ({
        line: row.line,
        values: row.values,
        action: row.action,
        mergeIntoId: row.existingId,
      })),
    );
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
