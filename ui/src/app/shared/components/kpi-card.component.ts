import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  template: `
    <div class="label">{{ label }}</div>
    <div class="value">{{ value }}</div>
    @if (hint) {
      <div class="hint">{{ hint }}</div>
    }
  `,
  host: { '[class]': 'flat ? "kpi" : "card kpi"' },
})
export class KpiCardComponent {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) value!: string | number;
  @Input() hint?: string;
  /** Renders without the card chrome, for KPIs nested inside another card. */
  @Input() flat = false;
}
