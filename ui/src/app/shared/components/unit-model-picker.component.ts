import { Component, EventEmitter, Output, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { UnitModelApi } from '../../core/api/unit-model.api';
import { formatDecimal, parseDecimal } from '../../core/catalog/decimal-text';
import { Anchor, PlannedUnit, fold, planModel } from '../../core/catalog/unit-model-plan';
import { ApplyUnitModelRequest, UnitModel } from '../../core/models';

/**
 * Ticks units of a model of units — "Poids": g, kg, tonne — for one product, each with its
 * price, so they need not be typed again product after product.
 *
 * <p>Asks for the anchor ("1 kg = 3,5 kapoka") only when the product shares no unit with the
 * model; greys out what the server would refuse (a gram of rice counted in kapoka) and proposes
 * each price from the base price, which the operator corrects.
 *
 * <p>The parent reads {@link request} when it saves, and {@link blocking} to keep its button off
 * while a ticked unit has no price.
 */
@Component({
  selector: 'app-unit-model-picker',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="ump">
      @if (models().length === 0) {
        <div class="muted ump-empty">
          Aucun modèle d'unités.
          <button class="link" type="button" (click)="manage.emit()">Créer un modèle</button>
        </div>
      } @else {
        <div class="ump-head">
          <label class="ump-label" [for]="fieldId() + '-model'">Modèle</label>
          <select
            class="field"
            [id]="fieldId() + '-model'"
            [ngModel]="model()?.id ?? null"
            (ngModelChange)="pickModel($event)"
          >
            @for (m of models(); track m.id) {
              <option [ngValue]="m.id">{{ m.name }} ({{ labelsOf(m) }})</option>
            }
          </select>
          <button class="link" type="button" (click)="manage.emit()">Gérer les modèles</button>
        </div>

        @if (needsAnchor()) {
          <!-- The model and the product share no unit: how they meet is the operator's to say. -->
          <div class="ump-anchor">
            <span>1</span>
            <select class="field" aria-label="Unité du modèle" [ngModel]="shownAnchorLabel()" (ngModelChange)="anchorLabel.set($event)">
              @for (label of anchorChoices(); track label) {
                <option [ngValue]="label">{{ label }}</option>
              }
            </select>
            <span>=</span>
            <input
              class="ump-qty"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="3,5"
              aria-label="Combien d'unités de base"
              [ngModel]="anchorText()"
              (ngModelChange)="anchorText.set($event)"
            />
            <span>{{ baseName() }}</span>
          </div>
        }

        <ul class="ump-units">
          @for (u of plan()?.units ?? []; track u.label) {
            <li [class.off]="u.status !== 'ok'">
              <label class="ump-check">
                <input
                  type="checkbox"
                  [disabled]="u.status !== 'ok'"
                  [checked]="isTicked(u)"
                  (change)="toggle(u)"
                />
                <strong>{{ u.label }}</strong>
                <span class="muted">{{ describe(u) }}</span>
              </label>
              @if (isTicked(u)) {
                <span class="ump-price">
                  <input
                    type="text"
                    inputmode="decimal"
                    autocomplete="off"
                    placeholder="prix"
                    [attr.aria-label]="'Prix de vente par ' + u.label"
                    [ngModel]="prices()[key(u)] ?? ''"
                    (ngModelChange)="setPrice(u, $event)"
                  />
                  Ar
                </span>
              }
            </li>
          }
        </ul>
        @if (blocking()) {
          <div class="ump-warn">Indiquez le prix de vente de chaque unité cochée.</div>
        }
      }
    </div>
  `,
  styles: `
    .ump-head,
    .ump-anchor {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 10px;

      select {
        width: auto;
        min-width: 0;
        max-width: 100%;
      }
    }

    .ump-label {
      font-size: 12px;
      font-weight: 600;
    }

    .ump-anchor {
      background: var(--brand-soft);
      border-radius: 8px;
      padding: 8px 10px;
      font-size: 13px;
    }

    .ump-qty {
      width: 80px;
    }

    .ump-units {
      list-style: none;
      margin: 0;
      padding: 0;

      li {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        flex-wrap: wrap;
        padding: 6px 0;
        border-bottom: 1px solid var(--line);
      }

      li.off {
        opacity: 0.6;
      }
    }

    .ump-check {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
      min-width: 0;
      cursor: pointer;
      font-size: 13px;

      .muted {
        font-size: 12px;
        overflow-wrap: anywhere;
      }
    }

    .ump-price {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12.5px;

      input {
        width: 110px;
      }
    }

    .ump-warn {
      color: var(--red);
      font-size: 12px;
      margin-top: 6px;
    }

    .ump-empty {
      font-size: 12.5px;
    }

    .link {
      border: none;
      background: none;
      padding: 0;
      color: var(--brand-ink);
      cursor: pointer;
      font-size: 12.5px;
      text-decoration: underline;
    }
  `,
})
export class UnitModelPickerComponent {
  private readonly api = inject(UnitModelApi);

  /** The product's base unit as it stands or is being typed; null for bare units. */
  readonly baseUnit = input<string | null>(null);
  readonly basePrice = input<number | null>(null);
  /** Units the product already sells in: not offered again, and they may place the model. */
  readonly packagings = input<readonly { label: string; factor: number }[]>([]);
  readonly fieldId = input('ump');

  /** "Gérer les modèles": the parent opens the models dialog, then calls {@link reload}. */
  @Output() readonly manage = new EventEmitter<void>();

  private readonly resource = apiResource<UnitModel[]>([], () => this.api.models());
  protected readonly models = this.resource.value;

  private readonly modelId = signal<number | null>(null);
  protected readonly anchorLabel = signal<string | null>(null);
  protected readonly anchorText = signal('');
  /** Price typed per ticked unit, by folded label; a unit is ticked while it has an entry. */
  protected readonly prices = signal<Partial<Record<string, string>>>({});

  protected readonly model = computed<UnitModel | null>(() => {
    const models = this.models();
    return models.find((m) => m.id === this.modelId()) ?? models[0] ?? null;
  });

  protected readonly baseName = computed(() => this.baseUnit()?.trim() || 'unité');

  private readonly product = computed(() => ({
    baseUnit: this.baseUnit()?.trim() || null,
    basePrice: this.basePrice(),
    packagings: this.packagings(),
  }));

  protected readonly needsAnchor = computed(() => {
    const model = this.model();
    return model !== null && planModel(model, this.product(), null).needsAnchor;
  });

  protected readonly anchorChoices = computed(() => {
    const model = this.model();
    return model ? [model.baseLabel, ...model.units.map((u) => u.label)] : [];
  });

  /** The unit the anchor is said in: the one picked, else the model's first unit. */
  protected readonly shownAnchorLabel = computed(() => this.anchorLabel() ?? this.defaultAnchor());

  private readonly anchor = computed<Anchor | null>(() => {
    const quantity = parseDecimal(this.anchorText());
    const label = this.shownAnchorLabel();
    return quantity && label ? { label, quantity } : null;
  });

  protected readonly plan = computed(() => {
    const model = this.model();
    return model ? planModel(model, this.product(), this.anchor()) : null;
  });

  /** Ticked units the model can still place — one greyed out since is dropped, not sent. */
  private readonly ticked = computed(() =>
    (this.plan()?.units ?? []).filter((u) => u.status === 'ok' && this.prices()[this.key(u)] !== undefined),
  );

  /** A unit is ticked without a usable price. */
  readonly blocking = computed(() =>
    this.ticked().some((u) => !(parseDecimal(this.prices()[this.key(u)] ?? '') ?? 0)),
  );

  /** What to send, or null when nothing usable is ticked. */
  readonly request = computed<ApplyUnitModelRequest | null>(() => {
    const model = this.model();
    const ticked = this.ticked();
    if (!model || ticked.length === 0 || this.blocking()) {
      return null;
    }
    const anchor = this.needsAnchor() ? this.anchor() : null;
    return {
      modelId: model.id,
      anchorLabel: anchor?.label ?? null,
      anchorQuantity: anchor?.quantity ?? null,
      units: ticked.map((u) => ({ label: u.label, price: parseDecimal(this.prices()[this.key(u)] ?? '')! })),
    };
  });

  /** After the models dialog: a model may have been added, renamed or removed. */
  reload(): void {
    this.resource.reload();
  }

  /** Back to nothing ticked, once the units were added. */
  clear(): void {
    this.prices.set({});
  }

  protected pickModel(id: number): void {
    this.modelId.set(id);
    this.anchorLabel.set(null);
    this.prices.set({});
  }

  protected labelsOf(model: UnitModel): string {
    return [model.baseLabel, ...model.units.map((u) => u.label)].join(', ');
  }

  protected key(unit: PlannedUnit): string {
    return fold(unit.label);
  }

  protected isTicked(unit: PlannedUnit): boolean {
    return unit.status === 'ok' && this.prices()[this.key(unit)] !== undefined;
  }

  protected toggle(unit: PlannedUnit): void {
    this.prices.update((prices) => {
      const next = { ...prices };
      if (next[this.key(unit)] !== undefined) {
        delete next[this.key(unit)];
      } else {
        next[this.key(unit)] = unit.suggestedPrice === null ? '' : String(unit.suggestedPrice);
      }
      return next;
    });
  }

  protected setPrice(unit: PlannedUnit, text: string): void {
    this.prices.update((prices) => ({ ...prices, [this.key(unit)]: text }));
  }

  protected describe(unit: PlannedUnit): string {
    const base = this.baseName();
    switch (unit.status) {
      case 'ok':
        return `= ${formatDecimal(unit.factor!)} ${base}`;
      case 'base':
        return 'unité de base';
      case 'present':
        return 'déjà présente';
      case 'no-anchor':
        return '';
      case 'too-precise':
        return `trop petite : moins d'un millième de ${base}`;
      case 'same-as-base':
        return `= 1 ${base} : c'est l'unité de base`;
    }
  }

  /** The model's first unit — "kg" for Poids — which is the one people know the weight of. */
  private defaultAnchor(): string | null {
    const model = this.model();
    return model ? (model.units[0]?.label ?? model.baseLabel) : null;
  }
}
