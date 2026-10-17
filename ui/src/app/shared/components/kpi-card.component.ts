import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * A figure with its label. Given a `link` — or listened to through `opened` — the card opens what
 * it counts: "Références en alerte" leads to those products, filtered. Clickable cards are
 * reachable with the keyboard and say so on hover.
 */
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
  host: {
    '[class]': '(flat ? "kpi" : "card kpi") + (clickable ? " kpi-link" : "")',
    '[attr.role]': 'clickable ? "link" : null',
    '[attr.tabindex]': 'clickable ? 0 : null',
    '[attr.title]': 'clickable ? "Voir le détail" : null',
    '(click)': 'open()',
    '(keydown.enter)': 'open()',
  },
})
export class KpiCardComponent {
  private readonly router = inject(Router);

  @Input({ required: true }) label!: string;
  @Input({ required: true }) value!: string | number;
  @Input() hint?: string;
  /** Renders without the card chrome, for KPIs nested inside another card. */
  @Input() flat = false;
  /** Where a click leads: the list this figure counts. */
  @Input() link: string | null = null;
  @Input() linkQuery: Record<string, string> | null = null;
  /** Makes the card clickable without leaving the page: the screen applies its own filter. */
  @Input() action = false;
  @Output() readonly opened = new EventEmitter<void>();

  protected get clickable(): boolean {
    return this.link !== null || this.action;
  }

  protected open(): void {
    if (!this.clickable) {
      return;
    }
    this.opened.emit();
    if (this.link) {
      void this.router.navigate([this.link], { queryParams: this.linkQuery ?? undefined });
    }
  }
}
