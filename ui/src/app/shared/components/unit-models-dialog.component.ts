import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { apiResource } from '../../core/api/api-resource';
import { UnitModelApi } from '../../core/api/unit-model.api';
import { formatDecimal, parseDecimal } from '../../core/catalog/decimal-text';
import { UnitModel, UnitModelUnit } from '../../core/models';

/** A unit being added to one model, as typed. */
interface UnitDraft {
  label: string;
  quantity: string;
  /** Another unit of the model by name; null for its reference unit. */
  reference: string | null;
}

const EMPTY_UNIT: UnitDraft = { label: '', quantity: '', reference: null };

/**
 * The models of units the shop reuses — "Poids": g, kg, tonne: create one, rename it, add or
 * remove its units, delete it.
 *
 * <p>Nothing here touches a product: a model's units were copied onto the products it was applied
 * to, so renaming or deleting it afterwards leaves them as they are — the dialog says so.
 *
 * <p>Échap is left to the dialog it is opened from, which closes this one first.
 */
@Component({
  selector: 'app-unit-models-dialog',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="modal-backdrop umd-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide umd" role="dialog" aria-modal="true" aria-labelledby="umd-title">
        <div class="modal-head">
          <div>
            <h2 id="umd-title">Modèles d'unités</h2>
            <div class="sub muted">
              Des unités à réutiliser d'un produit à l'autre, sans prix : cochez-les à la création
              d'un produit ou dans sa configuration. Les produits déjà faits ne changent pas si
              vous modifiez un modèle.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @for (m of models(); track m.id) {
            <section class="umd-model">
              <div class="umd-name">
                <input
                  type="text"
                  maxlength="40"
                  aria-label="Nom du modèle"
                  [ngModel]="names()[m.id] ?? m.name"
                  (ngModelChange)="setName(m.id, $event)"
                  (keydown.enter)="rename(m)"
                />
                @if ((names()[m.id] ?? m.name).trim() !== m.name) {
                  <button class="btn small" type="button" [disabled]="busy()" (click)="rename(m)">Renommer</button>
                }
                @if (deletingId() === m.id) {
                  <span class="muted">Supprimer le modèle ?</span>
                  <button class="btn small danger" type="button" [disabled]="busy()" (click)="remove(m)">Oui</button>
                  <button class="btn small ghost" type="button" (click)="deletingId.set(null)">Non</button>
                } @else {
                  <button class="btn small ghost" type="button" (click)="deletingId.set(m.id)">Supprimer</button>
                }
              </div>

              <ul class="umd-units">
                <li><strong>{{ m.baseLabel }}</strong> <span class="muted">unité de référence</span></li>
                @for (u of m.units; track u.id) {
                  <li>
                    <span>
                      <strong>{{ u.label }}</strong> = {{ written(u, m) }}
                    </span>
                    <button class="btn small ghost" type="button" [disabled]="busy()" (click)="removeUnit(m, u)">
                      Retirer
                    </button>
                  </li>
                }
              </ul>

              <form class="umd-add" (ngSubmit)="addUnit(m)">
                <input
                  name="label"
                  type="text"
                  maxlength="16"
                  placeholder="unité (sac 25 kg)"
                  aria-label="Nouvelle unité"
                  [ngModel]="draftOf(m.id).label"
                  (ngModelChange)="patchDraft(m.id, { label: $event })"
                />
                <span class="muted">contient</span>
                <input
                  name="quantity"
                  class="umd-qty"
                  type="text"
                  inputmode="decimal"
                  placeholder="25"
                  aria-label="Contenance"
                  [ngModel]="draftOf(m.id).quantity"
                  (ngModelChange)="patchDraft(m.id, { quantity: $event })"
                />
                <select
                  name="reference"
                  class="field"
                  aria-label="Unité de référence"
                  [ngModel]="draftOf(m.id).reference"
                  (ngModelChange)="patchDraft(m.id, { reference: $event })"
                >
                  <option [ngValue]="null">{{ m.baseLabel }}</option>
                  @for (u of m.units; track u.id) {
                    <option [ngValue]="u.label">{{ u.label }}</option>
                  }
                </select>
                <button class="btn small" type="submit" [disabled]="busy() || !canAdd(m.id)">Ajouter</button>
              </form>
            </section>
          } @empty {
            <div class="muted">Aucun modèle pour l'instant.</div>
          }

          <form class="umd-new" (ngSubmit)="create()">
            <div class="umd-new-title">Nouveau modèle</div>
            <div class="umd-new-fields">
              <input
                name="name"
                type="text"
                maxlength="40"
                placeholder="Nom (Volume, Cartons…)"
                aria-label="Nom du nouveau modèle"
                [ngModel]="newName()"
                (ngModelChange)="newName.set($event)"
              />
              <input
                name="base"
                type="text"
                maxlength="16"
                placeholder="unité de référence (ml, pièce…)"
                aria-label="Unité de référence du nouveau modèle"
                [ngModel]="newBase()"
                (ngModelChange)="newBase.set($event)"
              />
              <button class="btn small" type="submit" [disabled]="busy() || !newName().trim() || !newBase().trim()">
                Créer
              </button>
            </div>
          </form>
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    /* Above the product dialog it is opened from. */
    .umd-backdrop {
      z-index: 70;
    }

    .umd-model {
      border: 1px solid var(--line);
      border-radius: 9px;
      padding: 12px;
      margin-bottom: 12px;
    }

    .umd-name,
    .umd-add,
    .umd-new-fields {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;

      input {
        flex: 1 1 140px;
        min-width: 0;
      }

      select {
        width: auto;
        max-width: 100%;
      }
    }

    .umd-name input {
      font-weight: 700;
    }

    .umd-qty {
      flex: 0 1 80px !important;
    }

    .umd-units {
      list-style: none;
      padding: 0;
      margin: 10px 0;
      font-size: 13px;

      li {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 4px 0;
        border-bottom: 1px solid var(--line);
        overflow-wrap: anywhere;
      }
    }

    .umd-new {
      border: 1px dashed var(--line);
      border-radius: 9px;
      padding: 12px;
    }

    .umd-new-title {
      font-weight: 700;
      font-size: 12.5px;
      margin-bottom: 8px;
    }
  `,
})
export class UnitModelsDialogComponent {
  private readonly api = inject(UnitModelApi);

  /** After any write: the pickers reload their list of models. */
  @Output() readonly changed = new EventEmitter<void>();
  @Output() readonly closed = new EventEmitter<void>();

  private readonly resource = apiResource<UnitModel[]>([], () => this.api.models());
  protected readonly models = this.resource.value;

  protected readonly busy = signal(false);
  protected readonly deletingId = signal<number | null>(null);
  /** Names being typed, by model id; absent while unchanged. */
  protected readonly names = signal<Partial<Record<number, string>>>({});
  private readonly drafts = signal<Record<number, UnitDraft>>({});
  protected readonly newName = signal('');
  protected readonly newBase = signal('');

  protected written(unit: UnitModelUnit, model: UnitModel): string {
    const reference = unit.reference ?? model.baseLabel;
    const inBase = unit.reference ? ` (${formatDecimal(unit.baseFactor)} ${model.baseLabel})` : '';
    return `${formatDecimal(unit.quantity)} ${reference}${inBase}`;
  }

  protected setName(modelId: number, name: string): void {
    this.names.update((names) => ({ ...names, [modelId]: name }));
  }

  protected draftOf(modelId: number): UnitDraft {
    return this.drafts()[modelId] ?? EMPTY_UNIT;
  }

  protected patchDraft(modelId: number, change: Partial<UnitDraft>): void {
    this.drafts.update((drafts) => ({ ...drafts, [modelId]: { ...this.draftOf(modelId), ...change } }));
  }

  protected canAdd(modelId: number): boolean {
    const draft = this.draftOf(modelId);
    return !!draft.label.trim() && (parseDecimal(draft.quantity) ?? 0) > 0;
  }

  protected rename(model: UnitModel): void {
    const name = (this.names()[model.id] ?? model.name).trim();
    if (!name || name === model.name) {
      return;
    }
    this.run(this.api.rename(model.id, name), () => this.setName(model.id, name));
  }

  protected remove(model: UnitModel): void {
    this.run(this.api.delete(model.id), () => this.deletingId.set(null));
  }

  protected addUnit(model: UnitModel): void {
    const draft = this.draftOf(model.id);
    const quantity = parseDecimal(draft.quantity);
    if (!draft.label.trim() || !quantity) {
      return;
    }
    this.run(this.api.addUnit(model.id, { label: draft.label.trim(), quantity, reference: draft.reference }), () =>
      this.patchDraft(model.id, EMPTY_UNIT),
    );
  }

  protected removeUnit(model: UnitModel, unit: UnitModelUnit): void {
    this.run(this.api.removeUnit(model.id, unit.id));
  }

  protected create(): void {
    const name = this.newName().trim();
    const baseLabel = this.newBase().trim();
    if (!name || !baseLabel) {
      return;
    }
    this.run(this.api.create({ name, baseLabel, units: [] }), () => {
      this.newName.set('');
      this.newBase.set('');
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }

  /** Every write ends the same way: the list read again. A refusal's toast says what to fix. */
  private run(call: Observable<unknown>, after?: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.resource.reload();
        this.changed.emit();
      },
      error: () => this.busy.set(false),
    });
  }
}
