import { Component, Input, computed, signal } from '@angular/core';
import { RevenueBySeller } from '../../core/models';
import { AriaryPipe } from '../pipes/ariary.pipe';

@Component({
  selector: 'app-revenue-bars',
  standalone: true,
  imports: [AriaryPipe],
  template: `
    @if (rows().length) {
      @for (row of rows(); track row.sellerId) {
        <div class="bar-row">
          <div class="bname">{{ row.sellerName }}</div>
          <div class="bar-track">
            <div class="bar-fill" [style.width.%]="percentOf(row)"></div>
          </div>
          <div class="bval">{{ row.amount | ariary }}</div>
        </div>
      }
    } @else {
      <div class="empty">Aucune vente encaissée aujourd'hui.</div>
    }
  `,
})
export class RevenueBarsComponent {
  protected readonly rows = signal<readonly RevenueBySeller[]>([]);

  @Input({ required: true }) set data(value: readonly RevenueBySeller[]) {
    this.rows.set(value ?? []);
  }

  private readonly max = computed(() => Math.max(1, ...this.rows().map((r) => r.amount)));

  protected percentOf(row: RevenueBySeller): number {
    return Math.round((row.amount / this.max()) * 100);
  }
}
