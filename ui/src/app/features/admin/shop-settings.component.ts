import { Component, computed, inject, signal } from '@angular/core';
import { ModuleChange, menuChanges, switchEffect } from '../../core/config/settings-impact';
import { SettingsApi } from '../../core/api/settings.api';
import {
  BUSINESS_TYPES,
  BusinessTypeInfo,
  ShopFeatures,
  ShopSettings,
  businessTypeInfo,
  hasCashTrail,
  normalizeFeatures,
} from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';

interface FeatureRow {
  key: keyof ShopFeatures;
  label: string;
  hint: string;
  /** What this switch means nothing without; greyed out while it does not hold. */
  dependsOn?: (features: ShopFeatures) => boolean;
}

/**
 * How the shop works: the type of business, then the switches it pre-fills.
 *
 * <p>Picking a type ticks the switches that suit it, and each can still be changed — a type
 * is a starting point. What depends on a switch that is off is greyed out rather than hidden,
 * so the screen always shows the same rows in the same places.
 */
@Component({
  selector: 'app-shop-settings',
  standalone: true,
  imports: [ConfirmDialogComponent],
  template: `
    <div class="card">
      <h2>Type d'activité <small>coche les réglages adaptés, modifiables ensuite</small></h2>
      <div class="types" role="radiogroup" aria-label="Type d'activité">
        @for (info of types; track info.type) {
          <button
            type="button"
            class="type"
            role="radio"
            [attr.aria-checked]="draft().businessType === info.type"
            [class.active]="draft().businessType === info.type"
            (click)="pickType(info)"
          >
            <span class="ic" aria-hidden="true">{{ info.icon }}</span>
            <span class="name">{{ info.label }}</span>
            <span class="desc">{{ info.description }}</span>
          </button>
        }
      </div>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Fonctionnement <small>ce que voient les employés dépend de ces réglages</small></h2>
      <ul class="switches">
        @for (row of rows(); track row.key) {
          <li [class.off]="!available(row)">
            <label>
              <input
                type="checkbox"
                [checked]="effective()[row.key]"
                [disabled]="!available(row)"
                (change)="toggle(row.key, $any($event.target).checked)"
              />
              <span>
                <strong>{{ row.label }}</strong>
                <span class="hint">{{ row.hint }}</span>
                @if (effectOf(row); as effect) {
                  <span class="effect">Ajoute au menu : {{ effect }}</span>
                }
              </span>
            </label>
          </li>
        }
      </ul>

      <div class="foot">
        <span class="muted">
          @if (dirty()) {
            Modifications non enregistrées.
          } @else {
            Configuration en vigueur.
          }
        </span>
        <button class="btn" type="button" [disabled]="!dirty() || saving()" (click)="reviewing.set(true)">
          {{ saving() ? 'Enregistrement...' : 'Enregistrer' }}
        </button>
      </div>
    </div>

    @if (reviewing()) {
      <!-- These switches change the menu of the whole staff: said in full before it happens. -->
      <app-confirm-dialog
        title="Enregistrer la configuration ?"
        [message]="
          changes().length
            ? 'Le menu de tout le personnel change dès sa prochaine connexion :'
            : 'Le menu du personnel ne change pas.'
        "
        confirmLabel="Enregistrer"
        [busy]="saving()"
        (confirmed)="save()"
        (closed)="reviewing.set(false)"
      >
        @if (changes().length) {
          <ul class="changes">
            @for (change of changes(); track change.role) {
              <li>
                <strong>{{ change.module }}</strong>
                @for (line of linesOf(change); track line) {
                  <span class="line">{{ line }}</span>
                }
              </li>
            }
          </ul>
        }
        <ul class="changes">
          @for (line of switched(); track line) {
            <li>{{ line }}</li>
          }
        </ul>
      </app-confirm-dialog>
    }
  `,
  styles: `
    .types {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
      gap: 12px;
    }

    .type {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
      padding: 14px;
      border: 2px solid var(--line);
      border-radius: 12px;
      background: var(--panel);
      text-align: left;
      cursor: pointer;
      font: inherit;
      color: inherit;

      &.active {
        border-color: var(--brand);
        background: var(--brand-soft);
      }

      .ic {
        font-size: 22px;
      }

      .name {
        font-weight: 700;
        font-size: 15px;
      }

      .desc {
        font-size: 12.5px;
        color: var(--ink-soft);
      }
    }

    /* Two per row on a phone, so the switches below are reached without a long scroll. */
    @media (max-width: 560px) {
      .types {
        grid-template-columns: 1fr 1fr;
        gap: 8px;
      }

      .type {
        padding: 10px;

        .ic {
          font-size: 18px;
        }

        .name {
          font-size: 14px;
        }

        .desc {
          font-size: 11.5px;
        }
      }
    }

    .switches {
      list-style: none;
      margin: 0;
      padding: 0;

      li {
        padding: 12px 0;
        border-bottom: 1px solid var(--line);

        &.off {
          opacity: 0.5;
        }
      }

      label {
        display: flex;
        gap: 12px;
        align-items: flex-start;
        cursor: pointer;
      }

      input {
        width: 22px;
        height: 22px;
        margin-top: 1px;
        flex: 0 0 auto;
      }

      .hint {
        display: block;
        font-size: 12.5px;
        color: var(--ink-soft);
        margin-top: 2px;
      }

      .effect {
        display: block;
        font-size: 12.5px;
        color: var(--brand-ink);
        margin-top: 2px;
      }
    }

    .changes {
      margin: 10px 0 0;
      padding-left: 20px;
      font-size: 14px;

      li {
        margin: 4px 0;
      }

      .line {
        display: block;
        color: var(--ink-soft);
      }
    }

    .foot {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-top: 16px;
    }
  `,
})
export class ShopSettingsComponent {
  private readonly api = inject(SettingsApi);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);

  protected readonly types = BUSINESS_TYPES;
  protected readonly saving = signal(false);

  /** Starts from what the session carries: the configuration every screen is following now. */
  protected readonly draft = signal<ShopSettings>({ ...this.auth.settings() });

  /** What the server will keep: a switch whose prerequisite is off counts as off. */
  protected readonly effective = computed<ShopSettings>(() => ({
    ...this.draft(),
    ...normalizeFeatures(this.draft()),
  }));

  protected readonly dirty = computed(() => {
    const saved = this.auth.settings();
    const now = this.effective();
    return (Object.keys(now) as (keyof ShopSettings)[]).some((key) => saved[key] !== now[key]);
  });

  /** Worded with the type's own vocabulary: "la cuisine" rather than "le dépôt" in a restaurant. */
  protected readonly rows = computed<FeatureRow[]>(() => {
    const where = businessTypeInfo(this.draft().businessType).vocabulary.depot;
    return [
      {
        key: 'separateDelivery',
        label: `Préparation et remise séparées — ${where}`,
        hint:
          `Les commandes attendent d'être préparées et remises (${where}). ` +
          'Décoché : le client repart avec ses articles dès la vente.',
      },
      {
        key: 'payAtDepot',
        label: `Paiement accepté à la remise — ${where}`,
        hint:
          "Une commande non payée est encaissée au moment de la remise, et l'argent est " +
          'rapporté ensuite à la caisse. Décoché : tout se paie à la caisse.',
        dependsOn: (f) => f.separateDelivery,
      },
      {
        key: 'orderTakerCollects',
        label: 'La prise de commande peut encaisser',
        hint:
          "Réceptionniste ou serveur : encaisse le client en espèces, puis rapporte l'argent à " +
          'la caisse (écran « Argent à remettre »). Décoché : la prise de commande ne touche ' +
          "jamais l'argent.",
      },
      {
        key: 'dualControlRemittance',
        label: "Double contrôle de l'argent rapporté",
        hint:
          'Le versement doit être confirmé par une autre personne que celle qui l’a apporté. ' +
          'À décocher si une seule personne fait tout.',
        dependsOn: hasCashTrail,
      },
      {
        key: 'onlineOrdering',
        label: 'Commande en ligne par QR code',
        hint:
          'Le client, connecté au Wi-Fi de l’établissement, scanne le QR code de sa table et ' +
          'commande depuis son téléphone. Refusé depuis Internet. Les tables et leurs QR codes ' +
          'se gèrent dans « Tables & QR codes ».',
        dependsOn: (f) => f.separateDelivery,
      },
      {
        key: 'cancelAfterDelivery',
        label: 'Annulation possible après la remise',
        hint:
          'Pour une commande non payée déjà remise (client parti sans payer) : elle est annulée ' +
          'avec un commentaire obligatoire, sans retour en stock. Décoché : une commande remise ' +
          'ne peut plus être annulée.',
      },
    ];
  });

  /** The summary shown before saving. */
  protected readonly reviewing = signal(false);

  /** The menus the draft changes, compared with the configuration in force. */
  protected readonly changes = computed(() => menuChanges(this.auth.settings(), this.effective()));

  /** The type and the switches changed, in plain words: what the menus do not show. */
  protected readonly switched = computed(() => {
    const saved = this.auth.settings();
    const now = this.effective();
    const type =
      saved.businessType !== now.businessType
        ? [`Type d'activité : ${businessTypeInfo(saved.businessType).label} → ${businessTypeInfo(now.businessType).label}`]
        : [];
    return [
      ...type,
      ...this.rows()
        .filter((row) => saved[row.key] !== now[row.key])
        .map((row) => `${now[row.key] ? 'Activé' : 'Désactivé'} : ${row.label}`),
    ];
  });

  protected linesOf(change: ModuleChange): string[] {
    const quoted = (labels: string[]) => labels.map((label) => `« ${label} »`).join(', ');
    return [
      ...(change.added.length ? [`ajouté : ${quoted(change.added)}`] : []),
      ...(change.removed.length ? [`retiré : ${quoted(change.removed)}`] : []),
      ...change.renamed.map(({ from, to }) => `« ${from} » s'appelle « ${to} »`),
    ];
  }

  /** "Ajoute au menu : …" under a switch, so its weight is seen before it is ticked. */
  protected effectOf(row: FeatureRow): string {
    return switchEffect(this.effective(), row.key);
  }

  protected available(row: FeatureRow): boolean {
    return !row.dependsOn || row.dependsOn(this.effective());
  }

  protected pickType(info: BusinessTypeInfo): void {
    this.draft.set({ businessType: info.type, ...info.defaults });
  }

  protected toggle(key: keyof ShopFeatures, value: boolean): void {
    this.draft.update((draft) => ({ ...draft, [key]: value }));
  }

  protected save(): void {
    this.saving.set(true);
    this.api.update(this.effective()).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.reviewing.set(false);
        this.auth.applySettings(saved);
        this.draft.set({ ...saved });
        this.toasts.show(
          `Configuration enregistrée : ${businessTypeInfo(saved.businessType).label}. ` +
            'Les autres appareils la prennent en compte à leur prochaine connexion.',
        );
      },
      error: () => this.saving.set(false),
    });
  }
}
