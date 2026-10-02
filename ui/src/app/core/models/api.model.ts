import { PaymentMethod, PaymentStatus, RoleApp } from './enums';
import { Category } from './category.model';
import { Invoice } from './invoice.model';
import { Product } from './product.model';
import { ShopSettings } from './shop-settings.model';
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
  /** How the shop works; null while signed out. */
  settings: ShopSettings | null;
}

/** POST /api/invoices/{id}/pay — the amount is the order's total, read on the server. */
export interface PayInvoiceRequest {
  paymentMethod: PaymentMethod;
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
  /**
   * `packagingId` is the unit sold — "kg", "sac" — or null for the base unit; `quantity` is in
   * that unit, to the thousandth.
   */
  lines: { productId: number; packagingId?: number | null; quantity: number }[];
}

export interface CreateSupplyRequest {
  productId: number;
  supplierId: number;
  /** The unit the goods came in — "sac 50 kg" — or null for the base unit. */
  packagingId: number | null;
  /** In that unit: 10 for ten sacks. */
  quantity: number;
  /**
   * What one of that unit cost on this delivery — the sack's price. Null is accepted — the supplier's invoice sometimes
   * comes later — but such a receipt teaches the average cost nothing.
   */
  unitCost: number | null;
}

/**
 * POST /api/users. The handle is lower-cased by the server, since sign-in matches it
 * exactly; the screen says so rather than silently changing what was typed.
 */
export interface CreateUserRequest {
  fullName: string;
  username: string;
  password: string;
  roles: RoleApp[];
}

/**
 * PUT /api/users/{id} — the name and the whole new set of roles.
 *
 * <p>No handle and no password: the login handle is fixed once created, and a password goes
 * through its own endpoint so a rename cannot reset one by accident.
 */
export interface UpdateUserRequest {
  fullName: string;
  roles: RoleApp[];
}

/** PUT /api/users/{id}/password — the administrator hands a new one, no old one asked. */
export interface SetPasswordRequest {
  password: string;
}

/**
 * Mirrors UserAccountService.MIN_PASSWORD_LENGTH. Checked here only so the screen can say
 * what is wrong before the round trip — the server refuses anything shorter regardless.
 */
export const MIN_PASSWORD_LENGTH = 6;

/** Mirrors the @Pattern on CreateUserRequest.username: no spaces in something people type. */
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,30}$/;

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

/**
 * What one rayon is worth. Rows are per rayon, not rolled up into their parent: "Boissons"
 * counts the goods filed directly under it, "Boissons > Eau" its own, so the figures still
 * add up to the shop's total.
 */
export interface CategoryStock {
  category: Category;
  /** The rayon with its ancestors, since a bare name is ambiguous once there is a tree. */
  path: string;
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
  /** Slips brought by the depot and not confirmed yet, every agent's. */
  remittancesToConfirm: number;
  remittancesToConfirmAmount: number;
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
  /**
   * What the screen needs to act on a business refusal, shaped by `code` — the short lines of
   * an `INSUFFICIENT_STOCK`, for one (see `stockShortagesOf`). Absent for most errors.
   */
  data?: unknown;
}

/** Convenience for the guards: ROLE_ prefixed authority of a role. */
export const authorityOf = (role: RoleApp): string => `ROLE_${role}`;
