import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CatalogApi } from '../../core/api/catalog.api';
import { BarcodeAssignment, CodeTarget, codeTargetsOf } from '../../core/catalog/barcode-assignment';
import { ProductUnits } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { CameraScanButtonComponent } from './camera-scan-button.component';

/** Whose code a typed or scanned one already is — another product's, named. */
interface Taken {
  readonly key: string;
  readonly code: string;
  readonly name: string;
}

/** The row of a unit: the base one is keyed "base", the others by their id. */
function keyOf(target: CodeTarget): string {
  return target.packagingId === null ? 'base' : String(target.packagingId);
}

/**
 * The "Codes-barres" tab: one line per unit — the piece, the packet, the carton — with its code,
 * typed, read by a scanner (which types it and presses Entrée) or by the phone's camera.
 *
 * <p>A line's code is selected whole when the cursor enters it: a scanned code replaces it rather
 * than being added to its end.
 *
 * <p>A code another product already carries is said at once, with that product's name, before
 * anything is sent; the server refuses it anyway. Each line is saved on its own: changing the
 * carton's code touches nothing else of the product.
 */
@Component({
  selector: 'app-barcodes-editor',
  standalone: true,
  imports: [CameraScanButtonComponent],
  template: `
    <div class="bc">
      @for (target of targets(); track keyOf(target)) {
        @let key = keyOf(target);
        <div class="bc-row">
          <label class="bc-unit" [for]="'bc-' + key">
            {{ target.label }}
            @if (target.packagingId === null) {
              <span class="muted">(base)</span>
            }
          </label>
          <div class="bc-field">
            <input
              type="text"
              autocomplete="off"
              maxlength="64"
              placeholder="scanner ou saisir"
              [id]="'bc-' + key"
              [value]="draftOf(target)"
              (input)="type(target, $any($event.target).value)"
              (focus)="$any($event.target).select()"
              (blur)="check(target)"
              (keydown.enter)="$event.preventDefault(); save(target)"
            />
            <app-camera-scan-button (scanned)="scanned(target, $event)" />
            @if (edited(target)) {
              <button class="btn small" type="button" [disabled]="busy() || isTaken(target)" (click)="save(target)">
                Enregistrer
              </button>
            }
          </div>
          @if (takenFor(target); as other) {
            <div class="bc-taken">Ce code est déjà celui de « {{ other.name }} ».</div>
          }
        </div>
      }
      <p class="muted bc-note">
        Avec une douchette : cliquez dans la ligne, scannez, le code est enregistré. Un champ vidé retire le code.
      </p>
    </div>
  `,
  styles: `
    .bc-row {
      display: grid;
      grid-template-columns: minmax(110px, 180px) 1fr;
      align-items: center;
      gap: 6px 12px;
      padding: 8px 0;
      border-bottom: 1px solid var(--line);
    }

    .bc-unit {
      font-weight: 600;
      font-size: 13.5px;
    }

    .bc-field {
      display: flex;
      gap: 6px;
      min-width: 0;

      input {
        flex: 1;
        min-width: 0;
        max-width: 320px;
      }

      app-camera-scan-button:empty {
        display: none;
      }
    }

    .bc-taken {
      grid-column: 2;
      color: var(--red);
      font-size: 12.5px;
      font-weight: 600;
    }

    .bc-note {
      font-size: 12px;
      margin: 10px 0 0;
    }

    @media (max-width: 600px) {
      .bc-row {
        grid-template-columns: 1fr;
      }

      .bc-taken {
        grid-column: 1;
      }
    }
  `,
})
export class BarcodesEditorComponent {
  private readonly api = inject(CatalogApi);
  private readonly assignment = inject(BarcodeAssignment);
  private readonly toasts = inject(ToastService);

  readonly units = input.required<ProductUnits>();
  /** A code was saved: the units as the server now holds them. */
  readonly changed = output<ProductUnits>();

  protected readonly keyOf = keyOf;
  protected readonly targets = computed(() => codeTargetsOf(this.units()));
  /** What is typed in each line, by key; a line not typed in shows its code. */
  private readonly drafts = signal<ReadonlyMap<string, string>>(new Map());
  private readonly taken = signal<readonly Taken[]>([]);
  protected readonly busy = signal(false);

  protected draftOf(target: CodeTarget): string {
    return this.drafts().get(keyOf(target)) ?? target.barcode ?? '';
  }

  protected edited(target: CodeTarget): boolean {
    return this.draftOf(target).trim() !== (target.barcode ?? '');
  }

  protected takenFor(target: CodeTarget): Taken | undefined {
    const code = this.draftOf(target).trim();
    return this.taken().find((other) => other.key === keyOf(target) && other.code === code);
  }

  protected isTaken(target: CodeTarget): boolean {
    return this.takenFor(target) !== undefined;
  }

  protected type(target: CodeTarget, text: string): void {
    this.drafts.update((drafts) => new Map(drafts).set(keyOf(target), text));
  }

  protected scanned(target: CodeTarget, code: string): void {
    this.type(target, code);
    this.save(target);
  }

  /** Says at once whose code it already is, if another product carries it. */
  protected check(target: CodeTarget): void {
    const code = this.draftOf(target).trim();
    if (!code || !this.edited(target)) {
      return;
    }
    this.api.productByCode(code).subscribe((owner) => {
      if (owner && owner.id !== this.units().productId) {
        this.taken.update((all) => [...all, { key: keyOf(target), code, name: owner.name }]);
      }
    });
  }

  protected save(target: CodeTarget): void {
    if (!this.edited(target) || this.busy() || this.isTaken(target)) {
      return;
    }
    const code = this.draftOf(target).trim();
    this.busy.set(true);
    this.assignment.assign(this.units().productId, target.packagingId, code).subscribe({
      next: (units) => {
        this.busy.set(false);
        this.drafts.update((drafts) => {
          const next = new Map(drafts);
          next.delete(keyOf(target));
          return next;
        });
        this.toasts.show(code ? `Code ${code} enregistré pour « ${target.label} ».` : `Code retiré de « ${target.label} ».`);
        this.changed.emit(units);
      },
      // Taken meanwhile, most often: the error toast names the product.
      error: () => this.busy.set(false),
    });
  }
}
