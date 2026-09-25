import { Injectable, computed, signal } from '@angular/core';
import { buildMockDatabase } from '../data/mock-data';
import {
  CashRemittance,
  Category,
  DeliveryStatus,
  Invoice,
  MovementType,
  Payment,
  PaymentMethod,
  PaymentStatus,
  Product,
  RemittanceStatus,
  RoleApp,
  Sale,
  SaleLine,
  StockMovement,
  StockOutput,
  Supplier,
  Supply,
  UserApp,
  calculateTotal,
  isStockOutput,
  isSupply,
} from '../models';
import { isToday, isWithinLastDays, nowIso } from '../utils/date.util';

/** A product runs low below this quantity. */
export const LOW_STOCK_THRESHOLD = 10;

/** One cart entry of the cash-desk sale screen. */
export interface CartItem {
  product: Product;
  quantity: number;
}

export interface RevenueBySeller {
  seller: UserApp;
  amount: number;
  saleCount: number;
}

/**
 * In-memory replacement for the Spring Data repositories: the demo keeps the
 * whole dataset in signals so every screen reacts to the others' actions.
 *
 * Cross-entity references follow the JPA model (an Invoice *holds* its Sale).
 * Mutations therefore re-publish the owning aggregate too — see patchSale().
 */
@Injectable({ providedIn: 'root' })
export class ShopStore {
  private readonly seed = buildMockDatabase();
  private readonly sequences = { ...this.seed.sequences };

  readonly users = signal<UserApp[]>(this.seed.users);
  readonly categories = signal<Category[]>(this.seed.categories);
  readonly products = signal<Product[]>(this.seed.products);
  readonly suppliers = signal<Supplier[]>(this.seed.suppliers);
  readonly sales = signal<Sale[]>(this.seed.sales);
  readonly invoices = signal<Invoice[]>(this.seed.invoices);
  readonly payments = signal<Payment[]>(this.seed.payments);
  readonly remittances = signal<CashRemittance[]>(this.seed.remittances);
  readonly movements = signal<StockMovement[]>(this.seed.movements);

  // ------------------------------------------------------------- selectors

  readonly lowStockProducts = computed(() =>
    this.products().filter((p) => p.stockQuantity < LOW_STOCK_THRESHOLD),
  );

  readonly stockValue = computed(() =>
    this.products().reduce((total, p) => total + p.price * p.stockQuantity, 0),
  );

  readonly paidSales = computed(() =>
    this.sales().filter((s) => s.paymentStatus === PaymentStatus.PAID),
  );

  readonly salesToday = computed(() => this.sales().filter((s) => isToday(s.saleDate)));

  readonly invoicesToday = computed(() => this.invoices().filter((i) => isToday(i.invoiceDate)));

  readonly pendingDeliveries = computed(() =>
    this.invoices().filter((i) => i.deliveryStatus === DeliveryStatus.PENDING),
  );

  readonly deliveredInvoices = computed(() =>
    this.invoices().filter((i) => i.deliveryStatus === DeliveryStatus.DELIVERED),
  );

  readonly unpaidInvoices = computed(() =>
    this.invoices().filter((i) => i.paymentStatus === PaymentStatus.UNPAID),
  );

  readonly supplies = computed(() => this.movements().filter(isSupply));

  readonly stockOutputs = computed(() => this.movements().filter(isStockOutput));

  readonly suppliesLastWeek = computed(() =>
    this.supplies().filter((s) => isWithinLastDays(s.movementDate, 7)),
  );

  /** Cash collected at the depot that no agent has handed over yet. */
  readonly depotCashInHand = computed(() =>
    this.payments().filter(
      (p) => p.collectedBy.role === RoleApp.DEPOT_AGENT && p.cashRemittance === null,
    ),
  );

  readonly pendingRemittances = computed(() =>
    this.remittances().filter((r) => r.status === RemittanceStatus.PENDING),
  );

  readonly confirmedRemittances = computed(() =>
    this.remittances().filter((r) => r.status === RemittanceStatus.CONFIRMED),
  );

  /** Today's revenue per seller, best seller first. Only paid sales count. */
  readonly revenueBySeller = computed<RevenueBySeller[]>(() => {
    const perSeller = new Map<number, RevenueBySeller>();
    for (const sale of this.paidSales().filter((s) => isToday(s.saleDate))) {
      const current = perSeller.get(sale.seller.id);
      if (current) {
        current.amount += sale.totalAmount;
        current.saleCount += 1;
      } else {
        perSeller.set(sale.seller.id, {
          seller: sale.seller,
          amount: sale.totalAmount,
          saleCount: 1,
        });
      }
    }
    return [...perSeller.values()].sort((a, b) => b.amount - a.amount);
  });

  readonly revenueToday = computed(() =>
    this.revenueBySeller().reduce((total, row) => total + row.amount, 0),
  );

  // -------------------------------------------------- parameterised reads

  invoicesOf(seller: UserApp): Invoice[] {
    return this.invoices().filter((i) => i.sale.seller.id === seller.id);
  }

  salesOf(seller: UserApp): Sale[] {
    return this.sales().filter((s) => s.seller.id === seller.id);
  }

  /** Cash the given agent collected on delivery and has not remitted yet. */
  cashInHandOf(agent: UserApp): Payment[] {
    return this.payments().filter(
      (p) => p.collectedBy.id === agent.id && p.cashRemittance === null,
    );
  }

  remittancesOf(agent: UserApp): CashRemittance[] {
    return this.remittances().filter((r) => r.submittedBy.id === agent.id);
  }

  paymentsOf(invoice: Invoice): Payment[] {
    return this.payments().filter((p) => p.invoice.id === invoice.id);
  }

  static total(amounts: readonly { amount: number }[]): number {
    return amounts.reduce((sum, item) => sum + item.amount, 0);
  }

  // ------------------------------------------------------------ operations

  /**
   * Cash-desk checkout: writes the Sale with its lines, the Invoice, the stock
   * outputs, and — when the client pays right away — the matching Payment.
   */
  registerSale(input: {
    seller: UserApp;
    clientName: string;
    cart: readonly CartItem[];
    paymentStatus: PaymentStatus;
    paymentMethod: PaymentMethod;
  }): Invoice {
    const saleDate = nowIso();
    const lines: SaleLine[] = input.cart.map((item) => ({
      id: this.sequences.saleLine++,
      quantity: item.quantity,
      unitPrice: item.product.price,
      product: item.product,
    }));

    const sale: Sale = {
      id: this.sequences.sale++,
      saleDate,
      paymentStatus: input.paymentStatus,
      totalAmount: calculateTotal(lines),
      seller: input.seller,
      lines,
    };

    const invoice: Invoice = {
      id: this.sequences.invoice++,
      invoiceNumber: `F-${this.sequences.invoiceNumber++}`,
      invoiceDate: saleDate,
      clientName: input.clientName.trim() || 'Client comptoir',
      paymentStatus: input.paymentStatus,
      deliveryStatus: DeliveryStatus.PENDING,
      printed: false,
      sale,
    };

    this.sales.update((list) => [sale, ...list]);
    this.invoices.update((list) => [invoice, ...list]);

    for (const line of lines) {
      this.adjustStock(line.product.id, -line.quantity);
      this.addMovement<StockOutput>({
        movementType: MovementType.OUTPUT,
        quantity: line.quantity,
        movementDate: saleDate,
        product: line.product,
        performedBy: input.seller,
        invoice,
      });
    }

    if (input.paymentStatus === PaymentStatus.PAID) {
      this.addPayment(invoice, input.paymentMethod, input.seller, saleDate);
    }

    return invoice;
  }

  /**
   * Depot hand-over. An unpaid order is settled in cash on the spot, which puts
   * the money in the agent's hands until they remit it to the cash desk.
   */
  deliverInvoice(invoiceId: number, agent: UserApp): { invoice: Invoice; collected: number } | null {
    const invoice = this.invoices().find((i) => i.id === invoiceId);
    if (!invoice || invoice.deliveryStatus === DeliveryStatus.DELIVERED) {
      return null;
    }

    const collected = invoice.paymentStatus === PaymentStatus.UNPAID ? invoice.sale.totalAmount : 0;
    if (collected > 0) {
      this.addPayment(invoice, PaymentMethod.CASH, agent, nowIso());
      this.patchSale(invoice.sale.id, { paymentStatus: PaymentStatus.PAID });
    }

    this.patchInvoice(invoiceId, {
      deliveryStatus: DeliveryStatus.DELIVERED,
      ...(collected > 0 ? { paymentStatus: PaymentStatus.PAID } : {}),
    });

    return { invoice: this.invoices().find((i) => i.id === invoiceId)!, collected };
  }

  /** Bundles everything the agent holds into one pending remittance slip. */
  submitRemittance(agent: UserApp): CashRemittance | null {
    const held = this.cashInHandOf(agent);
    if (held.length === 0) {
      return null;
    }

    const remittance: CashRemittance = {
      id: this.sequences.remittance++,
      amount: ShopStore.total(held),
      remittanceDate: nowIso(),
      status: RemittanceStatus.PENDING,
      submittedBy: agent,
      confirmedBy: null,
    };

    const heldIds = new Set(held.map((p) => p.id));
    this.remittances.update((list) => [remittance, ...list]);
    this.payments.update((list) =>
      list.map((p) => (heldIds.has(p.id) ? { ...p, cashRemittance: remittance } : p)),
    );

    return remittance;
  }

  /** The cash desk acknowledges the money physically arrived. */
  confirmRemittance(remittanceId: number, cashier: UserApp): CashRemittance | null {
    const remittance = this.remittances().find((r) => r.id === remittanceId);
    if (!remittance || remittance.status === RemittanceStatus.CONFIRMED) {
      return null;
    }

    const confirmed: CashRemittance = {
      ...remittance,
      status: RemittanceStatus.CONFIRMED,
      confirmedBy: cashier,
    };
    this.remittances.update((list) => list.map((r) => (r.id === remittanceId ? confirmed : r)));
    this.payments.update((list) =>
      list.map((p) => (p.cashRemittance?.id === remittanceId ? { ...p, cashRemittance: confirmed } : p)),
    );

    return confirmed;
  }

  /** Goods received from a supplier: raises the stock and records a SUPPLY movement. */
  registerSupply(input: {
    productId: number;
    supplierId: number;
    quantity: number;
    performedBy: UserApp;
  }): Supply | null {
    const product = this.products().find((p) => p.id === input.productId);
    const supplier = this.suppliers().find((s) => s.id === input.supplierId);
    if (!product || !supplier || input.quantity <= 0) {
      return null;
    }

    this.adjustStock(product.id, input.quantity);
    return this.addMovement<Supply>({
      movementType: MovementType.SUPPLY,
      quantity: input.quantity,
      movementDate: nowIso(),
      product: this.products().find((p) => p.id === product.id)!,
      performedBy: input.performedBy,
      supplier,
    });
  }

  /** Front-end counterpart of Invoice#print(). */
  markInvoicePrinted(invoiceId: number): void {
    this.patchInvoice(invoiceId, { printed: true });
  }

  // --------------------------------------------------------------- internals

  /** Counterpart of Product#adjustStock(int). */
  private adjustStock(productId: number, delta: number): void {
    this.products.update((list) =>
      list.map((p) =>
        p.id === productId ? { ...p, stockQuantity: Math.max(0, p.stockQuantity + delta) } : p,
      ),
    );
  }

  private addPayment(
    invoice: Invoice,
    paymentMethod: PaymentMethod,
    collectedBy: UserApp,
    paymentDate: string,
  ): Payment {
    const payment: Payment = {
      id: this.sequences.payment++,
      amount: invoice.sale.totalAmount,
      paymentMethod,
      paymentDate,
      invoice,
      collectedBy,
      cashRemittance: null,
    };
    this.payments.update((list) => [payment, ...list]);
    return payment;
  }

  private addMovement<T extends StockMovement>(movement: Omit<T, 'id'>): T {
    const created = { ...movement, id: this.sequences.movement++ } as T;
    this.movements.update((list) => [created, ...list]);
    return created;
  }

  private patchInvoice(invoiceId: number, patch: Partial<Invoice>): void {
    this.invoices.update((list) =>
      list.map((i) => (i.id === invoiceId ? { ...i, ...patch } : i)),
    );
  }

  /** Re-publishes the sale and every invoice that owns it, keeping both in step. */
  private patchSale(saleId: number, patch: Partial<Sale>): void {
    let updated: Sale | undefined;
    this.sales.update((list) =>
      list.map((s) => {
        if (s.id !== saleId) {
          return s;
        }
        updated = { ...s, ...patch };
        return updated;
      }),
    );

    const freshSale = updated;
    if (freshSale) {
      this.invoices.update((list) =>
        list.map((i) => (i.sale.id === saleId ? { ...i, sale: freshSale } : i)),
      );
    }
  }
}
