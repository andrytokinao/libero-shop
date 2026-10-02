import {
  CashTrail,
  DeliveryStatus,
  Invoice,
  PaymentStatus,
  RoleApp,
  ShopFeatures,
} from '../models';
import { InvoiceActionContext, InvoiceActionKind, availableActions } from './invoice-actions';

const FATIMA = 1;
const JOSEPH = 3;

const FEATURES: ShopFeatures = {
  separateDelivery: true,
  payAtDepot: true,
  dualControlRemittance: false,
  cancelAfterDelivery: false,
  orderTakerCollects: false,
  onlineOrdering: false,
};

function order(
  paymentStatus: PaymentStatus,
  deliveryStatus = DeliveryStatus.PENDING,
  cashTrail: CashTrail | null = null,
): Invoice {
  return {
    id: 148,
    invoiceNumber: 'F-1148',
    invoiceDate: '2026-10-01T17:51:14',
    clientName: 'Client comptoir',
    paymentStatus,
    deliveryStatus,
    printed: false,
    itemCount: 6,
    sale: {
      id: 148,
      saleDate: '2026-10-01T17:51:14',
      paymentStatus,
      totalAmount: 20800,
      seller: { id: FATIMA, fullName: 'Fatima', username: 'fatima', roles: [RoleApp.CASHIER], enabled: true },
    },
    cancellation: null,
    cashTrail,
  } as Invoice;
}

function as(userId: number, roles: RoleApp[], features: Partial<ShopFeatures> = {}): InvoiceActionContext {
  return { userId, roles, features: { ...FEATURES, ...features }, handOverLabel: 'Remettre au client' };
}

/** The steps of the order's life — printing, offered beside most of them, is tested on its own. */
const kinds = (invoice: Invoice, ctx: InvoiceActionContext) =>
  availableActions(invoice, ctx)
    .map((action) => action.kind)
    .filter((kind) => kind !== InvoiceActionKind.PRINT);

describe('availableActions', () => {
  it('offers the till to take an unpaid order\'s money, or to cancel it', () => {
    expect(kinds(order(PaymentStatus.UNPAID), as(FATIMA, [RoleApp.CASHIER]))).toEqual([
      InvoiceActionKind.PAY,
      InvoiceActionKind.CANCEL,
    ]);
  });

  it('offers the depot to hand a pending order over, in the shop\'s own word', () => {
    const ctx = { ...as(JOSEPH, [RoleApp.DEPOT_AGENT]), handOverLabel: 'Servir' };
    const actions = availableActions(order(PaymentStatus.UNPAID), ctx);

    expect(actions.map((a) => a.kind)).toEqual([InvoiceActionKind.HAND_OVER]);
    expect(actions[0].label).toBe('Servir');
    expect(kinds(order(PaymentStatus.UNPAID, DeliveryStatus.DELIVERED), ctx)).toEqual([]);
  });

  it('offers to remit the cash to whoever holds it — and to nobody else', () => {
    const collected = order(PaymentStatus.COLLECTED, DeliveryStatus.DELIVERED, {
      holderId: FATIMA,
      holderName: 'Fatima',
      remittanceId: null,
    });

    const holder = availableActions(collected, as(FATIMA, [RoleApp.CASHIER, RoleApp.DEPOT_AGENT]));
    expect(kinds(collected, as(FATIMA, [RoleApp.CASHIER, RoleApp.DEPOT_AGENT]))).toEqual([
      InvoiceActionKind.REMIT_CASH,
    ]);
    expect(holder[0].confirm).toContain('20');

    expect(kinds(collected, as(JOSEPH, [RoleApp.DEPOT_AGENT]))).toEqual([]);
  });

  it('offers the till to confirm a slip — not to whoever brought it, when two people are required', () => {
    const remitted = order(PaymentStatus.REMITTED, DeliveryStatus.DELIVERED, {
      holderId: FATIMA,
      holderName: 'Fatima',
      remittanceId: 12,
    });

    expect(kinds(remitted, as(2, [RoleApp.CASHIER]))).toEqual([InvoiceActionKind.CONFIRM_REMITTANCE]);
    expect(availableActions(remitted, as(2, [RoleApp.CASHIER]))[0].label).toContain('V-12');
    expect(kinds(remitted, as(FATIMA, [RoleApp.CASHIER]))).toEqual([InvoiceActionKind.CONFIRM_REMITTANCE]);
    expect(kinds(remitted, as(FATIMA, [RoleApp.CASHIER], { dualControlRemittance: true }))).toEqual([]);
  });

  it('lets an order taker take the cash only when the shop allows it, and never a till twice', () => {
    const unpaid = order(PaymentStatus.UNPAID);

    expect(kinds(unpaid, as(5, [RoleApp.ORDER_TAKER]))).toEqual([]);
    expect(kinds(unpaid, as(5, [RoleApp.ORDER_TAKER], { orderTakerCollects: true }))).toEqual([
      InvoiceActionKind.COLLECT,
    ]);
    expect(
      kinds(unpaid, as(FATIMA, [RoleApp.CASHIER, RoleApp.ORDER_TAKER], { orderTakerCollects: true })),
    ).toEqual([InvoiceActionKind.PAY, InvoiceActionKind.CANCEL]);
  });

  it('offers nothing on a paid or cancelled order that has been handed over', () => {
    const everyone = as(FATIMA, [RoleApp.CASHIER, RoleApp.DEPOT_AGENT, RoleApp.SUPER_ADMIN]);

    expect(kinds(order(PaymentStatus.PAID, DeliveryStatus.DELIVERED), everyone)).toEqual([]);
    expect(kinds(order(PaymentStatus.CANCELLED, DeliveryStatus.CANCELLED), everyone)).toEqual([]);
  });

  it('lets the till print any order that stands, and says when it is a reprint', () => {
    const paid = order(PaymentStatus.PAID, DeliveryStatus.DELIVERED);
    const printOf = (invoice: Invoice, ctx: InvoiceActionContext) =>
      availableActions(invoice, ctx).find((a) => a.kind === InvoiceActionKind.PRINT);

    expect(printOf(paid, as(FATIMA, [RoleApp.CASHIER]))?.label).toBe('Imprimer');
    expect(printOf({ ...paid, printed: true }, as(FATIMA, [RoleApp.CASHIER]))?.label).toBe('Réimprimer');
    expect(printOf(paid, as(9, [RoleApp.SUPER_ADMIN]))).toBeDefined();
    expect(printOf(paid, as(JOSEPH, [RoleApp.DEPOT_AGENT]))).toBeUndefined();
    expect(printOf(order(PaymentStatus.CANCELLED), as(FATIMA, [RoleApp.CASHIER]))).toBeUndefined();
  });
});
