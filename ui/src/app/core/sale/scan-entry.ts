/**
 * What the cashier typed or scanned into the product field, read the way a till reads it:
 * an optional quantity, a star, then the code — "3*6111234567812" is three of that article.
 * A bare code is one of it.
 *
 * <p>A barcode scanner plugged into the computer types like a keyboard and ends with Enter, so
 * the same field serves the scanner, the short codes of loose goods ("12" for the baguette)
 * and a name typed by hand.
 */
export interface ScanEntry {
  readonly quantity: number;
  readonly code: string;
}

/** Beyond this a typing slip ("30*" for "3*") is likelier than a real order. */
export const MAX_SCAN_QUANTITY = 999;

const WITH_QUANTITY = /^(\d+)\s*\*\s*(.*)$/;

/** @returns null when there is nothing to look up — blank, or a quantity with no code yet */
export function parseScanEntry(raw: string): ScanEntry | null {
  const text = raw.trim();
  const match = WITH_QUANTITY.exec(text);
  const code = (match ? match[2] : text).trim();
  if (!code) {
    return null;
  }
  const quantity = match ? Number(match[1]) : 1;
  if (quantity < 1 || quantity > MAX_SCAN_QUANTITY) {
    return null;
  }
  return { quantity, code };
}
