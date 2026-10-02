import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models';

/**
 * One line the shelf could not fill when the sale was sent — mirrors
 * `InsufficientStockException.Shortage` on the server.
 */
export interface StockShortage {
  readonly productId: number;
  readonly productName: string;
  /** In the product's base unit, all its lines together. */
  readonly requested: number;
  /** What the shelf really holds now, in base units: the most its lines can ask for together. */
  readonly available: number;
  /** The base unit's name; null for bare units. */
  readonly unit?: string | null;
}

export const INSUFFICIENT_STOCK = 'INSUFFICIENT_STOCK';

/**
 * The short lines a failed sale reports, or none when it failed for another reason.
 * Read from the error's `data`, never from its message: the wording is for people.
 */
export function stockShortagesOf(error: unknown): StockShortage[] {
  if (!(error instanceof HttpErrorResponse) || error.status !== 409) {
    return [];
  }
  const body = error.error as ApiError | null;
  return body?.code === INSUFFICIENT_STOCK && Array.isArray(body.data)
    ? (body.data as StockShortage[])
    : [];
}
