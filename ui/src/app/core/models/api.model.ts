import { PaymentMethod, PaymentStatus, RoleApp } from './enums';
import { Category } from './category.model';
import { Invoice } from './invoice.model';
import { Product } from './product.model';
import { UserApp } from './user-app.model';

/**
 * Payloads that have no entity counterpart: requests, session state and the aggregated
 * figures the dashboards ask for in a single round trip.
 */

// ------------------------------------------------------------------- session

/** GET /api/auth/session, and the `session` of a LoginResponse. */
export interface Session {
  authenticated: boolean;
  user: UserApp | null;
  roleLabel: string | null;
  /** Landing route of the role, decided server-side. */
  homePath: string;
  /** e.g. ['ROLE_CASHIER'] — what the UI hides on, never what the server trusts. */
  authorities: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

/** POST /api/auth/login — the access token, and the session it stands for. */
export interface LoginResponse {
  /** Signed JWT, sent back as `Authorization: Bearer ...` on every later call. */
  accessToken: string;
  /** Always 'Bearer'. Sent by the server so the scheme is not hard-coded here. */
  tokenType: string;
  /** Seconds of validity, not a date: a browser clock that is off still expires it right. */
  expiresIn: number;
  session: Session;
}

// ------------------------------------------------------------------ requests

export interface CreateSaleRequest {
  clientName: string;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
  lines: { productId: number; quantity: number }[];
}

export interface CreateSupplyRequest {
  productId: number;
  supplierId: number;
  quantity: number;
}

// ----------------------------------------------------------------- responses

export interface Supplier {
  id: number;
  name: string;
  contact: string | null;
  suppliedProducts: string | null;
  deliveryCount: number;
  unitsReceived: number;
}

/** POST /api/invoices/{id}/deliver — the wording comes from the server. */
export interface DeliveryResult {
  invoice: Invoice;
  collected: number;
  message: string;
}

export interface RevenueBySeller {
  sellerId: number;
  sellerName: string;
  amount: number;
  saleCount: number;
}

export interface PaymentMethodBreakdown {
  paymentMethod: PaymentMethod;
  count: number;
  amount: number;
}

export interface CategoryStock {
  category: Category;
  references: number;
  units: number;
  value: number;
  sharePercent: number;
}

export interface UserActivity {
  user: UserApp;
  salesToday: number;
  collectedToday: number;
  deliveriesToday: number;
  remittances: number;
}

// ---------------------------------------------------------------- dashboards

export interface CashierDashboard {
  revenueToday: number;
  paidSalesToday: number;
  invoicesToday: number;
  unpaidInvoicesToday: number;
  averageBasket: number;
  outstandingToday: number;
  lowStockCount: number;
  latestInvoices: Invoice[];
}

export interface DepotDashboard {
  pendingDeliveries: number;
  unpaidPendingDeliveries: number;
  cashInHand: number;
  cashInHandCount: number;
  deliveredTotal: number;
  deliveredToday: number;
  deliveredValueToday: number;
  lowStockCount: number;
  pendingInvoices: Invoice[];
}

export interface StockDashboard {
  stockValue: number;
  referenceCount: number;
  unitsInStock: number;
  lowStockCount: number;
  suppliesLastWeek: number;
  unitsSuppliedLastWeek: number;
  deliveredInvoices: number;
  toRestock: Product[];
}

export interface AdminDashboard {
  revenueToday: number;
  stockValue: number;
  referenceCount: number;
  unpaidInvoices: number;
  unpaidAmount: number;
  pendingDeliveries: number;
  depotCashInHand: number;
  depotCashInHandCount: number;
  pendingRemittanceAmount: number;
  pendingRemittanceCount: number;
  confirmedRemittanceAmountToday: number;
  revenueBySeller: RevenueBySeller[];
  lowStockProducts: Product[];
}

export interface RevenueReport {
  totalToday: number;
  averageBasket: number;
  topSeller: RevenueBySeller | null;
  bySeller: RevenueBySeller[];
  byPaymentMethod: PaymentMethodBreakdown[];
}

/** Shape of every error body the API returns — see ApiExceptionHandler. */
export interface ApiError {
  code: string;
  message: string;
  details?: string[];
  timestamp: string;
}

/** Convenience for the guards: ROLE_ prefixed authority of a role. */
export const authorityOf = (role: RoleApp): string => `ROLE_${role}`;
