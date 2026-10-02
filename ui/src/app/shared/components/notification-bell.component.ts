import { DatePipe } from '@angular/common';
import { Component, ElementRef, HostListener, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNotification } from '../../core/models';
import { NotificationCenterService } from '../../core/realtime/notification-center.service';
import { IconComponent } from './icon.component';

/**
 * The bell beside the person: how many alerts are new, the list of the latest, and whether the
 * real-time link is up. At the foot of the sidebar on a desktop, in the phone's top bar.
 *
 * <p>The dot says the link's state because silence is ambiguous: "no sale since nine o'clock"
 * and "the socket dropped at nine" look the same from a storekeeper's chair, and only one of
 * them means work is waiting.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [DatePipe, IconComponent],
  host: { '[class.above]': "placement() === 'above'" },
  template: `
    <button
      class="bell"
      type="button"
      [attr.aria-expanded]="open()"
      [attr.aria-label]="'Notifications, ' + center.unread() + ' non lue(s)'"
      (click)="toggle()"
    >
      <app-icon name="bell" [size]="20" />
      @if (center.unread() > 0) {
        <span class="count">{{ center.unread() > 9 ? '9+' : center.unread() }}</span>
      }
      <span [class]="'dot ' + center.connection()" [title]="connectionLabel()"></span>
    </button>

    @if (open()) {
      <div class="panel" role="dialog" aria-label="Notifications">
        <div class="panel-head">
          <strong>Notifications</strong>
          <span class="muted">{{ connectionLabel() }}</span>
        </div>

        @if (center.browserAlerts() === 'default') {
          <button class="btn small ghost block" type="button" (click)="center.enableBrowserAlerts()">
            Être alerté même quand l'onglet est caché
          </button>
        }

        @for (item of center.items(); track item.id) {
          <button class="item" type="button" [class.actionable]="!!center.linkOf(item)" (click)="follow(item)">
            <span class="item-head">
              <strong>{{ item.title }}</strong>
              <span class="muted">{{ item.createdAt | date: 'HH:mm' }}</span>
            </span>
            <span class="item-body">{{ item.message }}</span>
          </button>
        } @empty {
          <div class="empty">Rien de nouveau depuis la connexion.</div>
        }
      </div>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-block;
    }

    /* Takes the colour of where it sits — the dark sidebar, the phone bar — like the icons beside it. */
    .bell {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: inherit;
      cursor: pointer;

      &:hover,
      &:focus-visible,
      &[aria-expanded='true'] {
        background: rgba(255, 255, 255, 0.12);
      }
    }

    .count {
      position: absolute;
      top: 0;
      right: -2px;
      min-width: 18px;
      padding: 1px 5px;
      border-radius: 9px;
      background: var(--red);
      color: #fff;
      font-size: 10.5px;
      font-weight: 700;
      line-height: 16px;
    }

    .dot {
      position: absolute;
      bottom: 3px;
      right: 3px;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--line);

      &.online {
        background: var(--brand);
      }

      &.connecting {
        background: var(--amber);
      }
    }

    .panel {
      position: absolute;
      right: 0;
      top: calc(100% + 8px);
      width: min(360px, 90vw);
      max-height: 420px;
      overflow-y: auto;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 10px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
      padding: 10px;
      z-index: 50;
      color: var(--ink);
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    /* At the foot of the sidebar: the panel opens upwards, from the bell's left edge. */
    :host(.above) .panel {
      top: auto;
      bottom: calc(100% + 8px);
      right: auto;
      left: 0;
    }

    .panel-head {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      font-size: 12.5px;
      margin-bottom: 2px;
    }

    .item {
      text-align: left;
      border: 1px solid var(--line);
      background: var(--bg);
      border-radius: 8px;
      padding: 8px 10px;
      font: inherit;
      cursor: default;
      display: flex;
      flex-direction: column;
      gap: 3px;

      &.actionable {
        cursor: pointer;
      }
    }

    .item-head {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      font-size: 12.5px;
    }

    .item-body {
      font-size: 12px;
      color: var(--ink-soft);
    }
  `,
})
export class NotificationBellComponent {
  protected readonly center = inject(NotificationCenterService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Where the panel opens: below the bell in a top bar, above it at the foot of the sidebar. */
  readonly placement = input<'below' | 'above'>('below');

  protected readonly open = signal(false);

  protected readonly connectionLabel = computed(() => {
    switch (this.center.connection()) {
      case 'online':
        return 'Temps réel connecté';
      case 'connecting':
        return 'Reconnexion…';
      default:
        return 'Temps réel hors ligne';
    }
  });

  protected toggle(): void {
    const opening = !this.open();
    this.open.set(opening);
    if (opening) {
      this.center.markAllRead();
    }
  }

  protected follow(item: AppNotification): void {
    const link = this.center.linkOf(item);
    if (link) {
      this.open.set(false);
      void this.router.navigateByUrl(link);
    }
  }

  /** A click anywhere else closes the panel, the way a menu is expected to. */
  @HostListener('document:click', ['$event'])
  protected closeOutside(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected close(): void {
    this.open.set(false);
  }
}
