import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import {
  CashRemittance,
  DeliveryStatus,
  OrderChange,
  OrderChangeKind,
  RemittanceStatus,
} from '../models';
import { AuthService } from '../services/auth.service';
import { anInvoice } from '../store/invoice.fixture';
import { InvoiceStore } from '../store/invoice.store';
import { RemittanceStore } from '../store/remittance.store';
import { ORDERS_TOPIC, OrderEvents } from './order-events.service';
import { RealtimeService } from './realtime.service';

/**
 * The one listener of `/topic/orders`, fed by a fake socket: each kind of change must land in the
 * stores the screens read, and a change it cannot apply must make the lists reload.
 */
describe('OrderEvents', () => {
  let topic: Subject<OrderChange>;
  let signedIn: ReturnType<typeof signal<boolean>>;
  let invoices: InvoiceStore;
  let remittances: RemittanceStore;
  let events: OrderEvents;

  beforeEach(() => {
    topic = new Subject<OrderChange>();
    signedIn = signal(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: RealtimeService,
          useValue: {
            state: signal('online'),
            topic: (name: string) => {
              expect(name).toBe(ORDERS_TOPIC);
              return topic.asObservable();
            },
          },
        },
        {
          provide: AuthService,
          useValue: { isAuthenticated: signedIn, currentUser: signal({ id: 7 }) },
        },
      ],
    });
    events = TestBed.inject(OrderEvents);
    invoices = TestBed.inject(InvoiceStore);
    remittances = TestBed.inject(RemittanceStore);
  });

  it('inserts a new order, then replaces it as it moves on', () => {
    topic.next(change(OrderChangeKind.ADDED, [anInvoice(1)]));
    expect(invoices.get(1)?.deliveryStatus).toBe(DeliveryStatus.PENDING);

    topic.next(change(OrderChangeKind.DELIVERED, [anInvoice(1, { deliveryStatus: DeliveryStatus.DELIVERED })]));
    expect(invoices.get(1)?.deliveryStatus).toBe(DeliveryStatus.DELIVERED);
    expect(invoices.entities().size).toBe(1);
  });

  it('applies a cash change to the slip and to its orders', () => {
    topic.next({ ...change(OrderChangeKind.CASH_CONFIRMED, [anInvoice(4)]), remittance: slip(30) });

    expect(invoices.get(4)).toBeDefined();
    expect(remittances.get(30)?.status).toBe(RemittanceStatus.CONFIRMED);
  });

  it('makes the lists reload when a change comes without its orders', () => {
    let reloads = 0;
    invoices.invalidated$.subscribe(() => reloads++);

    topic.next(change(OrderChangeKind.PAID, []));

    expect(reloads).toBe(1);
  });

  it('makes every list reload on a kind of change it does not know', () => {
    let reloads = 0;
    invoices.invalidated$.subscribe(() => reloads++);
    remittances.invalidated$.subscribe(() => reloads++);

    topic.next(change('REFUNDED' as OrderChangeKind, [anInvoice(1)]));

    expect(reloads).toBe(2);
    expect(invoices.get(1)).toBeUndefined();
  });

  it('tells the server-computed figures once the stores are written', () => {
    const seen: number[] = [];
    events.changes$.subscribe(() => seen.push(invoices.entities().size));

    topic.next(change(OrderChangeKind.ADDED, [anInvoice(1)]));

    expect(seen).toEqual([1]);
  });

  it('empties the stores on sign-out', () => {
    topic.next(change(OrderChangeKind.ADDED, [anInvoice(1)]));

    signedIn.set(false);
    TestBed.flushEffects();

    expect(invoices.entities().size).toBe(0);
  });
});

function change(kind: OrderChangeKind, orders: OrderChange['invoices']): OrderChange {
  return {
    change: kind,
    invoiceNumbers: orders.map((order) => order.invoiceNumber),
    invoices: orders,
    remittance: null,
  };
}

function slip(id: number): CashRemittance {
  const agent = { id: 3, fullName: 'Hery Depot', username: 'hery', roles: [], enabled: true };
  return {
    id,
    amount: 12000,
    remittanceDate: '2026-10-02T11:00:00',
    status: RemittanceStatus.CONFIRMED,
    submittedBy: agent,
    confirmedBy: { ...agent, id: 7, fullName: 'Fatima Caisse' },
    paymentCount: 1,
    invoices: [{ invoiceId: 4, invoiceNumber: 'F-1004', clientName: 'Rina', amount: 12000 }],
  };
}
