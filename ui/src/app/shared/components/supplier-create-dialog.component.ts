import { Component, ElementRef, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogApi } from '../../core/api/catalog.api';
import { Supplier } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';

/**
 * A wholesaler added without leaving the receipt: a name, a phone number if known. The screen
 * that opened it picks the new supplier at once.
 *
 * <p>The server refuses a name already on the list, however it is written; the error toast says
 * which one, and the form stays as typed.
 */
@Component({
  selector: 'app-supplier-create-dialog',
  standalone: true,
  imports: [FormsModule],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <form class="modal" role="dialog" aria-modal="true" aria-labelledby="sn-title" (ngSubmit)="create()">
        <div class="modal-head">
          <div>
            <h2 id="sn-title">Nouveau fournisseur</h2>
            <div class="sub muted">{{ hint() }}</div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          <div class="fld">
            <label for="sn-name">Nom</label>
            <input
              #nameField
              id="sn-name"
              name="name"
              type="text"
              autocomplete="off"
              maxlength="120"
              [ngModel]="name()"
              (ngModelChange)="name.set($event)"
            />
          </div>
          <div class="fld">
            <label for="sn-contact">Téléphone ou contact (facultatif)</label>
            <input
              id="sn-contact"
              name="contact"
              type="text"
              autocomplete="off"
              maxlength="120"
              placeholder="034 00 000 00"
              [ngModel]="contact()"
              (ngModelChange)="contact.set($event)"
            />
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="submit" [disabled]="busy() || !ready()">
            {{ busy() ? 'Enregistrement...' : 'Ajouter le fournisseur' }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class SupplierCreateDialogComponent {
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);

  /** Under the title: what happens to the new supplier on the screen that opened the dialog. */
  readonly hint = input('Il sera choisi pour cette réception.');
  readonly created = output<Supplier>();
  readonly closed = output<void>();

  protected readonly name = signal('');
  protected readonly contact = signal('');
  protected readonly busy = signal(false);
  protected readonly ready = computed(() => this.name().trim().length > 0);

  private readonly nameField = viewChild<ElementRef<HTMLInputElement>>('nameField');

  constructor() {
    afterNextRender(() => this.nameField()?.nativeElement.focus());
  }

  protected create(): void {
    if (!this.ready() || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.createSupplier({ name: this.name().trim(), contact: this.contact().trim() || null }).subscribe({
      next: (supplier) => {
        this.toasts.show(`Fournisseur « ${supplier.name} » ajouté.`);
        this.created.emit(supplier);
        this.closed.emit();
      },
      error: () => this.busy.set(false),
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
