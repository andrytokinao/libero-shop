/**
 * Ordering from a table's QR code — mirrors DiningTableResponse and the public DTOs of
 * PublicOrderController.
 */

/** A table and the token its QR code carries. */
export interface DiningTable {
  id: number;
  name: string;
  token: string;
}

/** GET /api/public/tables/{token}/menu — what a customer may see: no stock, no cost. */
export interface PublicMenu {
  tableName: string;
  items: PublicMenuItem[];
}

export interface PublicMenuItem {
  id: number;
  name: string;
  price: number;
  category: string | null;
  /** False once sold out. */
  available: boolean;
}

/** POST /api/public/tables/{token}/orders — the table is in the link, never in here. */
export interface PublicOrderRequest {
  clientName: string | null;
  lines: { productId: number; quantity: number }[];
}

export type PublicOrderStatus = 'RECEIVED' | 'SERVED' | 'CANCELLED';

export interface PublicOrder {
  invoiceNumber: string;
  tableName: string;
  total: number;
  status: PublicOrderStatus;
  paid: boolean;
  lines: { name: string; quantity: number }[];
}

/** Mirrors OnlineOrderService.MAX_UNITS_PER_LINE: checked here only to say it before the server does. */
export const MAX_ONLINE_UNITS_PER_LINE = 20;

/** The page a table's QR code opens, relative to a server address. */
export const tableOrderPath = (token: string): string => `/commander/${token}`;
