import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

/** The days a history is asked for, `yyyy-MM-dd`, both included. Empty: the server's default. */
export interface Period {
  from?: string;
  to?: string;
}

/**
 * "Du … au …" for a history of orders. Left empty, the server answers its default: the last 30
 * days and what is still open. Sits in a `.form-row`, next to the search box.
 */
@Component({
  selector: 'app-period-filter',
  standalone: true,
  imports: [FormsModule],
  styles: [':host { display: contents; }'],
  template: `
    <div class="fld">
      <label [for]="name() + '-from'">Du</label>
      <input
        [id]="name() + '-from'"
        type="date"
        [max]="to()"
        [ngModel]="from()"
        (ngModelChange)="onFrom($event)"
      />
    </div>
    <div class="fld">
      <label [for]="name() + '-to'">Au</label>
      <input
        [id]="name() + '-to'"
        type="date"
        [min]="from()"
        [ngModel]="to()"
        (ngModelChange)="onTo($event)"
      />
    </div>
    @if (from() || to()) {
      <div class="fld">
        <label>&nbsp;</label>
        <button type="button" class="btn ghost" (click)="clear()">30 derniers jours</button>
      </div>
    }
  `,
})
export class PeriodFilterComponent {
  /** Prefix of the inputs' ids: unique on the screen. */
  readonly name = input.required<string>();
  readonly changed = output<Period>();

  protected readonly from = signal('');
  protected readonly to = signal('');

  protected onFrom(value: string): void {
    this.from.set(value ?? '');
    this.emit();
  }

  protected onTo(value: string): void {
    this.to.set(value ?? '');
    this.emit();
  }

  protected clear(): void {
    this.from.set('');
    this.to.set('');
    this.emit();
  }

  private emit(): void {
    const from = this.from();
    const to = this.to();
    // A period typed backwards is not asked: the server would refuse it.
    if (from && to && to < from) {
      return;
    }
    this.changed.emit({ from: from || undefined, to: to || undefined });
  }
}
