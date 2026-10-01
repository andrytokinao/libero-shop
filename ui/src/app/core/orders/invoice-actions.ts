import { DeliveryStatus, Invoice, PaymentStatus, RoleApp, ShopFeatures } from '../models';

/** What can be done to an order from its detail — one per step of its life. */
export enum InvoiceActionKind {
  /** The till takes the money of an unpaid order. */
  PAY = 'PAY',
  /** An order taker takes the customer's cash, to bring to the till later. */
  COLLECT = 'COLLECT',
  /** The depot (kitchen, bar) hands the order over. */
  HAND_OVER = 'HAND_OVER',
  /** Whoever holds the order's cash brings it to the till. */
  REMIT_CASH = 'REMIT_CASH',
  /** The till confirms having counted the cash brought to it. */
  CONFIRM_REMITTANCE = 'CONFIRM_REMITTANCE',
  CANCEL = 'CANCEL',
}

/** Who is looking, and how the shop is set up: all a rule needs besides the order. */
export interface InvoiceActionContext {
  readonly userId: number | null;
  readonly roles: readonly RoleApp[];
  readonly features: ShopFeatures;
  /** Words of the shop: the depot's hand-over is "Servir" in a restaurant. */
  readonly handOverLabel: string;
}

/** A button of the detail: what it does, what it says, and how it looks. */
export interface InvoiceAction {
  readonly kind: InvoiceActionKind;
  readonly label: string;
  /** `primary` is the step the order is waiting for; `danger` undoes it. */
  readonly tone: 'primary' | 'ghost' | 'danger';
  /**
   * When set, the button asks this question and acts on a second press — for the steps that
   * move money in a way the person may not expect from the label alone.
   */
  readonly confirm?: string;
}

/**
 * One action and when it applies — a Specification per step, so the next step of an order's
 * life is one more rule here rather than one more condition in every screen.
 *
 * <p>Each rule mirrors the server's own check (PaymentTransition, DeliveryTransition, the
 * controllers' roles, the shop's switches). The server checks again: a rule here only spares
 * the person a button that would be refused.
 */
interface InvoiceActionRule {
  readonly kind: InvoiceActionKind;
  appliesTo(invoice: Invoice, ctx: InvoiceActionContext): boolean;
  describe(invoice: Invoice, ctx: InvoiceActionContext): Omit<InvoiceAction, 'kind'>;
}

const has = (ctx: InvoiceActionContext, ...roles: RoleApp[]) =>
  roles.some((role) => ctx.roles.includes(role));

const RULES: readonly InvoiceActionRule[] = [
  {
    kind: InvoiceActionKind.PAY,
    appliesTo: (invoice, ctx) =>
      has(ctx, RoleApp.CASHIER) && invoice.paymentStatus === PaymentStatus.UNPAID,
    describe: () => ({ label: 'Encaisser', tone: 'primary' }),
  },
  {
    kind: InvoiceActionKind.COLLECT,
    // A till pays instead: same money, but straight into the till.
    appliesTo: (invoice, ctx) =>
      has(ctx, RoleApp.ORDER_TAKER) &&
      !has(ctx, RoleApp.CASHIER) &&
      ctx.features.orderTakerCollects &&
      invoice.paymentStatus === PaymentStatus.UNPAID,
    describe: () => ({ label: 'Encaisser (espèces)', tone: 'primary' }),
  },
  {
    kind: InvoiceActionKind.HAND_OVER,
    appliesTo: (invoice, ctx) =>
      has(ctx, RoleApp.DEPOT_AGENT) && invoice.deliveryStatus === DeliveryStatus.PENDING,
    describe: (_, ctx) => ({ label: ctx.handOverLabel, tone: 'primary' }),
  },
  {
    kind: InvoiceActionKind.REMIT_CASH,
    // Only the holder: the server remits nobody's cash but the caller's own.
    appliesTo: (invoice, ctx) =>
      has(ctx, RoleApp.DEPOT_AGENT, RoleApp.ORDER_TAKER) &&
      invoice.paymentStatus === PaymentStatus.COLLECTED &&
      !!invoice.cashTrail &&
      invoice.cashTrail.remittanceId === null &&
      invoice.cashTrail.holderId === ctx.userId,
    describe: (invoice) => ({
      label: "Remettre l'argent à la caisse",
      tone: 'primary',
      confirm: `Vous apportez ${invoice.sale.totalAmount.toLocaleString('fr-FR')} Ar à la caisse ?`,
    }),
  },
  {
    kind: InvoiceActionKind.CONFIRM_REMITTANCE,
    appliesTo: (invoice, ctx) =>
      has(ctx, RoleApp.CASHIER) &&
      invoice.paymentStatus === PaymentStatus.REMITTED &&
      !!invoice.cashTrail?.remittanceId &&
      // Two people when the shop asks for it: whoever brought the cash does not count it.
      !(ctx.features.dualControlRemittance && invoice.cashTrail.holderId === ctx.userId),
    describe: (invoice) => ({
      label: `Confirmer la réception (V-${invoice.cashTrail!.remittanceId})`,
      tone: 'primary',
      // A slip may carry several orders: confirming it confirms them all.
      confirm: `Argent du versement V-${invoice.cashTrail!.remittanceId} compté et reçu ? Toutes ses commandes passent payées.`,
    }),
  },
  {
    kind: InvoiceActionKind.CANCEL,
    // Mirrors InvoiceService.cancel: unpaid only; handed over only when the shop allows it; the
    // account's own orders, or any for a till or the administrator.
    appliesTo: (invoice, ctx) =>
      invoice.paymentStatus === PaymentStatus.UNPAID &&
      (invoice.deliveryStatus !== DeliveryStatus.DELIVERED || ctx.features.cancelAfterDelivery) &&
      (has(ctx, RoleApp.CASHIER, RoleApp.SUPER_ADMIN) || invoice.sale.seller.id === ctx.userId),
    describe: () => ({ label: 'Annuler la commande', tone: 'danger' }),
  },
];

/** The buttons this person may press on this order now, in the order of the order's life. */
export function availableActions(invoice: Invoice, ctx: InvoiceActionContext): InvoiceAction[] {
  return RULES.filter((rule) => rule.appliesTo(invoice, ctx)).map((rule) => ({
    kind: rule.kind,
    ...rule.describe(invoice, ctx),
  }));
}
