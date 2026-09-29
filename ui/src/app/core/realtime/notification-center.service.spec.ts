import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AppNotification, NotificationType, RoleApp } from '../models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { NotificationCenterService } from './notification-center.service';
import { RealtimeService } from './realtime.service';

function sale(id: string): AppNotification {
  return {
    id,
    type: NotificationType.SALE_CREATED,
    title: 'Nouvelle vente a preparer',
    message: `FAC-${id} - Hotely Vaha`,
    createdAt: '2026-09-29T09:00:00Z',
    data: null,
  };
}

/**
 * The bell's bookkeeping, fed by a fake transport: what the user is told is decided here, and
 * none of it should depend on a socket being open.
 */
describe('NotificationCenterService', () => {
  let incoming: Subject<AppNotification>;
  let signedIn: ReturnType<typeof signal<boolean>>;
  let roles: RoleApp[];
  let center: NotificationCenterService;
  let toasts: ToastService;

  beforeEach(() => {
    incoming = new Subject<AppNotification>();
    signedIn = signal(true);
    roles = [RoleApp.DEPOT_AGENT];

    TestBed.configureTestingModule({
      providers: [
        {
          provide: RealtimeService,
          useValue: { notifications$: incoming.asObservable(), state: signal('online') },
        },
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: signedIn,
            hasRole: (...wanted: RoleApp[]) => wanted.some((role) => roles.includes(role)),
          },
        },
      ],
    });
    center = TestBed.inject(NotificationCenterService);
    toasts = TestBed.inject(ToastService);
  });

  it('lists what arrives, newest first, and toasts it', () => {
    incoming.next(sale('1'));
    incoming.next(sale('2'));

    expect(center.items().map((item) => item.id)).toEqual(['2', '1']);
    expect(center.unread()).toBe(2);
    expect(toasts.message()).toContain('FAC-2');
  });

  it('counts only what came after the list was last opened', () => {
    incoming.next(sale('1'));
    center.markAllRead();
    incoming.next(sale('2'));

    expect(center.unread()).toBe(1);
  });

  it('ignores a notification received twice', () => {
    incoming.next(sale('1'));
    incoming.next(sale('1'));

    expect(center.items().length).toBe(1);
  });

  it('sends a storekeeper to the orders to hand over, a manager to the stock outputs', () => {
    expect(center.linkOf(sale('1'))).toBe('/depot/remise');

    roles = [RoleApp.DEPOT_MANAGER];
    expect(center.linkOf(sale('1'))).toBe('/gestion-depot/sorties');

    roles = [RoleApp.CASHIER];
    expect(center.linkOf(sale('1'))).toBeNull();
  });

  it('forgets everything on sign-out, so the next account starts clean', () => {
    incoming.next(sale('1'));

    signedIn.set(false);
    TestBed.flushEffects();

    expect(center.items()).toEqual([]);
    expect(center.unread()).toBe(0);
  });
});
