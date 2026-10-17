import { Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { OnlineOrderApi } from '../../core/api/online-order.api';
import { ServerApi } from '../../core/api/server.api';
import { DiningTable, ServerConnection, tableOrderPath } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { copyToClipboard } from '../../core/utils/clipboard.util';
import { ActionMenuComponent, MenuAction } from '../../shared/components/action-menu.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { QrCodeComponent } from '../../shared/components/qr-code.component';

type TableAction = 'rename' | 'copy' | 'print' | 'regenerate' | 'delete';

/** A table's "⋮": the printed code's life, from renaming it to withdrawing it. */
const TABLE_ACTIONS: readonly MenuAction<TableAction>[] = [
  { id: 'rename', label: 'Renommer', hint: 'même QR code' },
  { id: 'copy', label: 'Copier le lien' },
  { id: 'print', label: 'Imprimer ce QR code' },
  { id: 'regenerate', label: 'Nouveau code', hint: "l'ancien ne marche plus" },
  { id: 'delete', label: 'Supprimer', danger: true },
];

interface BaseAddress {
  url: string;
  label: string;
}

/**
 * The tables customers order from, each with its QR code to print and put on the table.
 *
 * <p>The code points at one of the server's addresses on the shop's Wi-Fi — the customer joins
 * the guest network, then scans. Never the Internet address: the server refuses table orders
 * from outside, so a photo of the code is useless from home. Printing hides everything but the
 * codes.
 *
 * <p>Each table's "⋮" renames it (same code), copies its link, prints its code alone, or withdraws
 * it: "Nouveau code" and "Supprimer" stop the printed code at once, so both ask first.
 */
@Component({
  selector: 'app-dining-tables',
  standalone: true,
  imports: [FormsModule, QrCodeComponent, ActionMenuComponent, ConfirmDialogComponent],
  template: `
    <div class="card no-print">
      <h2>Tables <small>une table = un QR code à poser dessus</small></h2>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:260px;">
          <label for="table-name">Nouvelle table</label>
          <input
            id="table-name"
            type="text"
            maxlength="40"
            placeholder="Ex : Table 4, Terrasse 2, Bar"
            [ngModel]="newName()"
            (ngModelChange)="newName.set($event)"
            (keydown.enter)="create()"
          />
        </div>
        <div class="fld" style="justify-content:flex-end;">
          <button class="btn" type="button" [disabled]="!newName().trim() || busy()" (click)="create()">
            Ajouter
          </button>
        </div>
        @if (bases().length > 1) {
          <div class="fld" style="flex:1; min-width:220px;">
            <label for="table-base">Adresse dans les QR codes</label>
            <select
              id="table-base"
              class="field"
              [ngModel]="base().url"
              (ngModelChange)="pickedBase.set($event)"
            >
              @for (b of bases(); track b.url) {
                <option [ngValue]="b.url">{{ b.label }} — {{ b.url }}</option>
              }
            </select>
          </div>
        }
      </div>
      <p class="muted small" style="margin:6px 0 0;">
        La commande se fait sur place uniquement : le client se connecte d'abord au Wi-Fi de
        l'établissement, puis scanne. Depuis Internet (4G, à distance), le serveur refuse.
      </p>
      @if (tables().length) {
        <button class="btn ghost" type="button" style="margin-top:12px;" (click)="print()">
          Imprimer les QR codes
        </button>
      }
    </div>

    @if (tables().length) {
      <div class="qr-grid">
        @for (table of tables(); track table.id) {
          <div class="qr-card" [class.no-print]="printing() !== null && printing() !== table.id">
            <div class="qr-head">
              <div class="qr-name">{{ table.name }}</div>
              <app-action-menu
                class="no-print"
                [actions]="actions"
                [label]="'Actions sur ' + table.name"
                (picked)="pick(table, $event)"
              />
            </div>
            <app-qr-code [value]="linkOf(table)" [size]="170" [alt]="'QR code ' + table.name" />
            <div class="qr-hint">Scannez pour commander</div>
            <div class="qr-url no-print">{{ linkOf(table) }}</div>
          </div>
        }
      </div>
    } @else {
      <div class="empty" style="margin-top:16px;">Aucune table : ajoutez-en une ci-dessus.</div>
    }

    @if (renaming(); as table) {
      <div class="modal-backdrop no-print" (click)="onBackdrop($event)">
        <form class="modal" role="dialog" aria-modal="true" aria-labelledby="tr-title" (ngSubmit)="rename(table)">
          <div class="modal-head">
            <div>
              <h2 id="tr-title">Renommer « {{ table.name }} »</h2>
              <div class="sub muted">Le QR code reste le même : celui qui est imprimé continue de marcher.</div>
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="renaming.set(null)">✕</button>
          </div>
          <div class="modal-body">
            <div class="fld">
              <label for="tr-name">Nouveau nom</label>
              <input
                #renameField
                id="tr-name"
                name="name"
                type="text"
                maxlength="40"
                autocomplete="off"
                [ngModel]="newTableName()"
                (ngModelChange)="newTableName.set($event)"
              />
            </div>
          </div>
          <div class="modal-foot">
            <button class="btn ghost" type="button" (click)="renaming.set(null)">Annuler</button>
            <button class="btn" type="submit" [disabled]="busy() || !canRename(table)">Renommer</button>
          </div>
        </form>
      </div>
    }

    @if (regenerating(); as table) {
      <app-confirm-dialog
        class="no-print"
        [title]="'Nouveau code pour ' + table.name + ' ?'"
        message="Le QR code imprimé ne fonctionnera plus : il faudra imprimer le nouveau et le poser sur la table."
        confirmLabel="Nouveau code"
        [busy]="busy()"
        (confirmed)="regenerate(table)"
        (closed)="regenerating.set(null)"
      />
    }

    @if (deleting(); as table) {
      <app-confirm-dialog
        class="no-print"
        [title]="'Supprimer ' + table.name + ' ?'"
        message="Son QR code ne fonctionnera plus. Les commandes déjà passées depuis cette table restent."
        confirmLabel="Supprimer"
        [danger]="true"
        [busy]="busy()"
        (confirmed)="remove(table)"
        (closed)="deleting.set(null)"
      />
    }
  `,
  styles: `
    .small {
      font-size: 12.5px;
    }

    .qr-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 12px;
      margin-top: 16px;
    }

    .qr-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 16px;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 12px;
      break-inside: avoid;
    }

    .qr-head {
      display: flex;
      align-items: center;
      justify-content: center;
      align-self: stretch;
      position: relative;

      app-action-menu {
        position: absolute;
        right: -6px;
      }
    }

    .qr-name {
      font-weight: 700;
      font-size: 18px;
    }

    .qr-hint {
      font-size: 13px;
      color: var(--ink-soft);
    }

    .qr-url {
      font-size: 11px;
      color: var(--ink-soft);
      word-break: break-all;
      text-align: center;
    }

    @media print {
      .qr-grid {
        grid-template-columns: repeat(3, 1fr);
        margin: 0;
      }

      .qr-card {
        border: 1px dashed #999;
      }
    }
  `,
})
export class DiningTablesComponent {
  private readonly api = inject(OnlineOrderApi);
  private readonly toasts = inject(ToastService);

  protected readonly newName = signal('');
  protected readonly busy = signal(false);
  protected readonly actions = TABLE_ACTIONS;
  protected readonly renaming = signal<DiningTable | null>(null);
  protected readonly newTableName = signal('');
  protected readonly regenerating = signal<DiningTable | null>(null);
  protected readonly deleting = signal<DiningTable | null>(null);
  /** The one table whose code is being printed alone; null prints them all. */
  protected readonly printing = signal<number | null>(null);
  private readonly renameField = viewChild<ElementRef<HTMLInputElement>>('renameField');

  constructor() {
    // The rename window opens with the current name selected, to be typed over.
    effect(() => {
      const field = this.renameField()?.nativeElement;
      if (field) {
        setTimeout(() => {
          field.focus();
          field.select();
        });
      }
    });
  }
  protected readonly pickedBase = signal<string | null>(null);

  private readonly resource = apiResource<DiningTable[]>([], () => this.api.tables());
  protected readonly tables = this.resource.value;

  private readonly serverApi = inject(ServerApi);
  private readonly connection = apiResource<ServerConnection | null>(null, () =>
    this.serverApi.connection(),
  );

  /**
   * The server's addresses on the shop's network, the real Wi-Fi card first. Never the Internet
   * one: the server answers table orders from the shop's own network only.
   */
  protected readonly bases = computed<BaseAddress[]>(() => {
    const c = this.connection.value();
    const list: BaseAddress[] = [];
    for (const a of [...(c?.addresses ?? [])].sort((x, y) => Number(x.likelyVirtual) - Number(y.likelyVirtual))) {
      list.push({ url: a.url, label: a.label });
    }
    return list.length ? list : [{ url: window.location.origin, label: 'Cette adresse' }];
  });

  protected readonly base = computed(
    () => this.bases().find((b) => b.url === this.pickedBase()) ?? this.bases()[0],
  );

  protected linkOf(table: DiningTable): string {
    return this.base().url.replace(/\/$/, '') + tableOrderPath(table.token);
  }

  protected create(): void {
    const name = this.newName().trim();
    if (!name || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.createTable(name).subscribe({
      next: () => {
        this.busy.set(false);
        this.newName.set('');
        this.resource.reload();
      },
      error: () => this.busy.set(false),
    });
  }

  protected pick(table: DiningTable, action: TableAction): void {
    switch (action) {
      case 'rename':
        this.newTableName.set(table.name);
        this.renaming.set(table);
        break;
      case 'copy':
        void this.copy(table);
        break;
      case 'print':
        this.printOne(table);
        break;
      case 'regenerate':
        this.regenerating.set(table);
        break;
      case 'delete':
        this.deleting.set(table);
        break;
    }
  }

  /**
   * A click beside the window closes it. Returns nothing: Angular cancels any event whose handler
   * answers false, and an inline "a && b" did so for every click inside — the form never sent.
   */
  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.renaming.set(null);
    }
  }

  protected canRename(table: DiningTable): boolean {
    const name = this.newTableName().trim();
    return name !== '' && name !== table.name;
  }

  protected rename(table: DiningTable): void {
    if (!this.canRename(table) || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.renameTable(table.id, this.newTableName().trim()).subscribe({
      next: (renamed) => {
        this.busy.set(false);
        this.renaming.set(null);
        this.resource.reload();
        this.toasts.show(`« ${table.name} » s'appelle maintenant « ${renamed.name} ». Son QR code ne change pas.`);
      },
      error: () => this.busy.set(false),
    });
  }

  /** Only this table's card on the page, the time of the print dialog. */
  private printOne(table: DiningTable): void {
    this.printing.set(table.id);
    // The other cards leave the page before the print dialog takes its picture of it.
    setTimeout(() => {
      window.print();
      this.printing.set(null);
    });
  }

  protected regenerate(table: DiningTable): void {
    this.busy.set(true);
    this.api.regenerate(table.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.regenerating.set(null);
        this.resource.reload();
        this.toasts.show(`${table.name} : nouveau QR code. L'ancien ne fonctionne plus, réimprimez-le.`);
      },
      error: () => this.busy.set(false),
    });
  }

  protected remove(table: DiningTable): void {
    this.busy.set(true);
    this.api.deleteTable(table.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.deleting.set(null);
        this.resource.reload();
        this.toasts.show(`${table.name} supprimée : son QR code ne fonctionne plus.`);
      },
      error: () => this.busy.set(false),
    });
  }

  protected async copy(table: DiningTable): Promise<void> {
    const ok = await copyToClipboard(this.linkOf(table));
    this.toasts.show(ok ? 'Lien copié.' : 'Copie impossible ici : sélectionnez le lien sous le QR code.');
  }

  protected print(): void {
    window.print();
  }
}
