import { Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { FirstSteps } from '../../core/models';
import { SetupRole, firstStepsOf, readiness } from '../../core/setup/first-steps';
import { reloadOnStockChange } from '../../core/realtime/reload-on';

const HIDDEN_STORAGE_KEY = 'liberoshop.firstSteps.hidden';

/**
 * "Votre boutique est prête à 50 %": the card a new shop sees on its dashboard, its boxes ticking
 * themselves as the catalogue, the stock and the accounts fill in. Gone once everything is done,
 * or on « Masquer » — kept on this device.
 */
@Component({
  selector: 'app-first-steps',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (visible()) {
      <section class="card fs" aria-labelledby="fs-title">
        <div class="fs-head">
          <h2 id="fs-title">
            Premiers pas
            <small>votre boutique est prête à {{ percent() }} %</small>
          </h2>
          <button class="btn small ghost" type="button" (click)="hide()">Masquer</button>
        </div>
        <div class="fs-bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="percent()">
          <span [style.width.%]="percent()"></span>
        </div>
        <ul class="fs-steps">
          @for (step of steps(); track step.key) {
            <li [class.done]="step.done">
              <span class="fs-box" aria-hidden="true">{{ step.done ? '☑' : '☐' }}</span>
              @if (step.done) {
                <span>{{ step.label }}</span>
              } @else if (step.createsProduct) {
                <button class="lnk" type="button" (click)="createProduct.emit()">{{ step.label }}</button>
              } @else if (step.link) {
                <a [routerLink]="step.link">{{ step.label }}</a>
              } @else {
                <span>{{ step.label }}</span>
              }
              @if (!step.done && step.note) {
                <small class="muted fs-note">— {{ step.note }}</small>
              }
              <span class="sr-only">{{ step.done ? '(fait)' : '(à faire)' }}</span>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    .fs {
      margin-bottom: 16px;
      border-left: 4px solid var(--brand);
    }

    .fs-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    .fs-bar {
      height: 8px;
      border-radius: 4px;
      background: var(--line);
      overflow: hidden;
      margin: 4px 0 12px;

      span {
        display: block;
        height: 100%;
        background: var(--brand);
      }
    }

    .fs-steps {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: 6px 18px;

      /* One line of text that wraps as a sentence, its note following the label. */
      li {
        font-size: 14px;
      }

      a {
        color: var(--brand);
      }

      li.done {
        color: var(--ink-soft);
      }

      .fs-box {
        font-size: 16px;
        margin-right: 6px;
      }

      .fs-note {
        margin-left: 4px;
      }

      .lnk {
        border: 0;
        background: none;
        padding: 0;
        font: inherit;
        color: var(--brand);
        text-decoration: underline;
        cursor: pointer;
      }
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `,
})
export class FirstStepsComponent {
  /** Decides where each step leads: an administrator creates accounts, a depot manager does not. */
  readonly role = input.required<SetupRole>();
  /** "Créer un premier produit": the dashboard opens its own "Nouveau produit". */
  readonly createProduct = output<void>();

  private readonly api = inject(DashboardApi);
  private readonly resource = apiResource<FirstSteps | null>(null, () => this.api.firstSteps());
  private readonly hidden = signal(storedHidden());

  protected readonly steps = computed(() => {
    const progress = this.resource.value();
    return progress ? firstStepsOf(progress, this.role()) : [];
  });
  protected readonly percent = computed(() => readiness(this.steps()));
  protected readonly visible = computed(
    () => !this.hidden() && this.steps().length > 0 && this.steps().some((step) => !step.done),
  );

  constructor() {
    // A product created, received or sold: the boxes tick themselves.
    reloadOnStockChange(this.resource);
  }

  /** Called by the dashboard after "Nouveau produit" or any change it made. */
  refresh(): void {
    this.resource.reload();
  }

  protected hide(): void {
    this.hidden.set(true);
    try {
      localStorage.setItem(HIDDEN_STORAGE_KEY, '1');
    } catch {
      // Private browsing: hidden as long as the screen.
    }
  }
}

function storedHidden(): boolean {
  try {
    return localStorage.getItem(HIDDEN_STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}
