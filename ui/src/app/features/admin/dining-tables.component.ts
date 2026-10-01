import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { OnlineOrderApi } from '../../core/api/online-order.api';
import { ServerApi } from '../../core/api/server.api';
import { DiningTable, ServerConnection, tableOrderPath } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { copyToClipboard } from '../../core/utils/clipboard.util';
import { QrCodeComponent } from '../../shared/components/qr-code.component';

interface BaseAddress {
  url: string;
  label: string;
}

/**
 * The tables customers order from, each with its QR code to print and put on the table.
 *
 * <p>The code points at one of the server's addresses: the shop's Wi-Fi by default — the
 * customer joins the guest network, then scans — or the Internet address when the server is
 * published there. Printing hides everything but the codes.
 *
 * <p>"Nouveau code" withdraws a code that went around: the printed one stops working at once,
 * so the button asks twice.
 */
@Component({
  selector: 'app-dining-tables',
  standalone: true,
  imports: [FormsModule, QrCodeComponent],
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
        Les clients doivent pouvoir joindre cette adresse : Wi-Fi de l'établissement, ou adresse
        Internet si le serveur est publié.
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
          <div class="qr-card">
            <div class="qr-name">{{ table.name }}</div>
            <app-qr-code [value]="linkOf(table)" [size]="170" [alt]="'QR code ' + table.name" />
            <div class="qr-hint">Scannez pour commander</div>
            <div class="qr-url no-print">{{ linkOf(table) }}</div>
            <div class="qr-actions no-print">
              <button class="btn small ghost" type="button" (click)="copy(table)">Copier le lien</button>
              @if (confirming() === 'regen-' + table.id) {
                <button class="btn small" type="button" [disabled]="busy()" (click)="regenerate(table)">
                  Oui, nouveau code
                </button>
              } @else {
                <button class="btn small ghost" type="button" (click)="confirming.set('regen-' + table.id)">
                  Nouveau code
                </button>
              }
              @if (confirming() === 'del-' + table.id) {
                <button class="btn small danger-btn" type="button" [disabled]="busy()" (click)="remove(table)">
                  Oui, supprimer
                </button>
              } @else {
                <button class="btn small ghost" type="button" (click)="confirming.set('del-' + table.id)">
                  Supprimer
                </button>
              }
            </div>
          </div>
        }
      </div>
    } @else {
      <div class="empty" style="margin-top:16px;">Aucune table : ajoutez-en une ci-dessus.</div>
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

    .qr-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 6px;
      margin-top: 4px;
    }

    .danger-btn {
      background: var(--red);
      border-color: var(--red);
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
  /** Which two-step button is waiting for its second tap: "regen-3", "del-3". */
  protected readonly confirming = signal<string | null>(null);
  protected readonly pickedBase = signal<string | null>(null);

  private readonly resource = apiResource<DiningTable[]>([], () => this.api.tables());
  protected readonly tables = this.resource.value;

  private readonly serverApi = inject(ServerApi);
  private readonly connection = apiResource<ServerConnection | null>(null, () =>
    this.serverApi.connection(),
  );

  /** Where the customer's phone can reach the server: the Internet one first when published. */
  protected readonly bases = computed<BaseAddress[]>(() => {
    const c = this.connection.value();
    const list: BaseAddress[] = [];
    if (c?.internet) {
      list.push({ url: c.internet.url, label: 'Internet' });
    }
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

  protected regenerate(table: DiningTable): void {
    this.busy.set(true);
    this.api.regenerate(table.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirming.set(null);
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
        this.confirming.set(null);
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
