import { DatePipe } from '@angular/common';
import { Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { DeliveryStatus, Invoice } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { IconComponent } from './icon.component';
import { UserAvatarComponent } from './user-avatar.component';

/**
 * Who is on an order: a service icon and their face, beside the order's status, once someone
 * has said "je m'en occupe". A tap says it in words — "En cours de service par Joseph depuis
 * 14:32" — so the next storekeeper knows to leave it, and whom to ask.
 *
 * <p>Renders nothing for an order nobody has taken on: drop it beside any order.
 */
@Component({
  selector: 'app-handler-chip',
  standalone: true,
  imports: [DatePipe, IconComponent, UserAvatarComponent],
  host: {
    '(document:click)': 'closeOutside($event)',
    '(document:keydown.escape)': 'open.set(false)',
  },
  template: `
    @if (handler(); as person) {
      <button
        type="button"
        class="chip"
        [class.mine]="mine()"
        [attr.aria-expanded]="open()"
        [attr.aria-label]="sentence()"
        (click)="open.set(!open())"
      >
        <app-icon name="handOver" [size]="15" />
        <app-user-avatar [user]="person" size="xs" [tooltip]="false" />
      </button>
      @if (open()) {
        <span class="note" role="status">
          {{ sentence() }}
          @if (invoice().handledSince; as since) {
            depuis {{ since | date: 'HH:mm' }}
          }
        </span>
      }
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
      vertical-align: middle;
      margin-left: 6px;
    }

    .chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 3px 2px 7px;
      border: 1px solid var(--amber);
      border-radius: 14px;
      background: var(--amber-soft);
      color: var(--amber);
      cursor: pointer;

      &.mine {
        border-color: var(--brand);
        background: var(--brand-soft);
        color: var(--brand-dark);
      }
    }

    .note {
      position: absolute;
      top: calc(100% + 6px);
      left: 0;
      z-index: 40;
      width: max-content;
      max-width: 240px;
      padding: 8px 10px;
      border-radius: 8px;
      background: var(--chrome-bg);
      color: var(--chrome-ink);
      font-size: 12px;
      line-height: 1.35;
      box-shadow: 0 6px 18px rgba(0, 0, 0, 0.2);
    }
  `,
})
export class HandlerChipComponent {
  private readonly auth = inject(AuthService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly invoice = input.required<Invoice>();

  protected readonly open = signal(false);

  /** Who is on it — only while it is in progress; once served it is history, not a warning. */
  protected readonly handler = computed(() => {
    const order = this.invoice();
    return order.deliveryStatus === DeliveryStatus.IN_PROGRESS ? (order.handledBy ?? null) : null;
  });

  protected readonly mine = computed(() => this.handler()?.id === this.auth.currentUser()?.id);

  protected readonly sentence = computed(() =>
    this.mine() ? 'Vous vous en occupez' : `En cours de service par ${this.handler()?.fullName ?? ''}`,
  );

  protected closeOutside(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
