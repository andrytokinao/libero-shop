import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { AppNotification, NotificationType, RoleApp } from '../models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { RealtimeService } from './realtime.service';

/** Enough to scroll back through a shift's worth of alerts without the list growing forever. */
const KEPT = 30;

/**
 * What the user is told about what arrives in real time: the toast, the bell's list and count,
 * and a system alert when the tab is in the background.
 *
 * <p>Kept apart from {@link RealtimeService} on purpose. That one carries messages; this one
 * decides how a person is told. A screen that only needs to refresh listens to the transport,
 * and never has to know a bell exists.
 *
 * <p>The list lives in memory and starts empty at each sign-in. The server does not store
 * notifications: each is a hint whose facts are in the REST API — a sale missed while the
 * screen was closed is still in the list of orders to hand over.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  readonly connection = inject(RealtimeService).state;

  private readonly received = signal<readonly AppNotification[]>([]);
  private readonly lastSeen = signal<string | null>(null);

  /** Newest first. */
  readonly items = this.received.asReadonly();
  readonly unread = computed(() => {
    const seen = this.lastSeen();
    const items = this.received();
    const index = seen === null ? -1 : items.findIndex((item) => item.id === seen);
    return index === -1 ? items.length : index;
  });

  constructor() {
    inject(RealtimeService).notifications$.subscribe((notification) => this.receive(notification));

    // A new account on the same screen must not read the previous one's alerts.
    effect(() => {
      if (!this.auth.isAuthenticated()) {
        untracked(() => {
          this.received.set([]);
          this.lastSeen.set(null);
        });
      }
    });
  }

  markAllRead(): void {
    this.lastSeen.set(this.received()[0]?.id ?? null);
  }

  /** Where acting on a notification happens, or null when it is only information. */
  linkOf(notification: AppNotification): string | null {
    switch (notification.type) {
      case NotificationType.SALE_CREATED:
        if (this.auth.hasRole(RoleApp.DEPOT_AGENT)) {
          return '/depot/remise';
        }
        return this.auth.hasRole(RoleApp.DEPOT_MANAGER) ? '/gestion-depot/sorties' : null;
      case NotificationType.ORDER_DELIVERED:
        return this.auth.hasRole(RoleApp.CASHIER) ? '/caisse/factures' : null;
      case NotificationType.REMITTANCE_SUBMITTED:
        return this.auth.hasRole(RoleApp.CASHIER) ? '/caisse/versements' : null;
      case NotificationType.REMITTANCE_CONFIRMED:
        return this.auth.hasRole(RoleApp.DEPOT_AGENT) ? '/depot/caisse' : null;
      default:
        return null;
    }
  }

  /** Whether the browser may be asked for, or already grants, system alerts. */
  browserAlerts(): NotificationPermission | 'unsupported' {
    return 'Notification' in window ? Notification.permission : 'unsupported';
  }

  /** Must be called from a click: browsers refuse a permission prompt nobody asked for. */
  enableBrowserAlerts(): void {
    if (this.browserAlerts() === 'default') {
      void Notification.requestPermission();
    }
  }

  private receive(notification: AppNotification): void {
    if (this.received().some((item) => item.id === notification.id)) {
      return;
    }
    this.received.update((items) => [notification, ...items].slice(0, KEPT));
    this.toasts.show(`${notification.title} — ${notification.message}`);
    this.alertIfHidden(notification);
  }

  /**
   * A storekeeper is often on another window, or another application altogether; a toast
   * in a hidden tab is a toast nobody reads.
   */
  private alertIfHidden(notification: AppNotification): void {
    if (document.visibilityState === 'visible' || this.browserAlerts() !== 'granted') {
      return;
    }
    const alert = new Notification(notification.title, {
      body: notification.message,
      tag: notification.id,
    });
    alert.onclick = () => {
      window.focus();
      alert.close();
    };
  }
}
