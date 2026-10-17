import { Product } from '../models';

/** Fewer letters than this find half the catalogue: the list waits for the second one. */
export const MIN_PICK_LETTERS = 2;

/**
 * Whether what was typed or scanned reads as a barcode rather than as words: six digits or more,
 * what a scanner types. Shorter numbers ("12" for the baguette, "5" in "riz 5 kg") stay words.
 */
export function looksLikeCode(text: string): boolean {
  return /^\d{6,}$/.test(text.trim());
}

/** A product found by a code: the unit the code is printed on comes with it (null: base unit). */
export interface CodeMatch {
  readonly product: Product;
  readonly packagingId: number | null;
}

/** Lower case, without accents or extra spaces: "cafe" finds "Café", as the server's search does. */
export function searchKey(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * The product carrying exactly this code — on its base unit or on a carton — or null. A scanner
 * types the whole code at once, so only an exact match counts: "600" is not "6001001000015".
 */
export function findByCode(products: readonly Product[], code: string): CodeMatch | null {
  const wanted = code.trim();
  if (!wanted) {
    return null;
  }
  for (const product of products) {
    if (product.barcode === wanted) {
      return { product, packagingId: null };
    }
    const packaging = product.packagings?.find((candidate) => candidate.barcode === wanted);
    if (packaging) {
      return { product, packagingId: packaging.id };
    }
  }
  return null;
}

/**
 * The products whose name holds every word typed, in any order — "riz 5" finds "Riz 5kg". Names
 * that start with what was typed come first. Nothing below {@link MIN_PICK_LETTERS} letters.
 */
export function matchProducts(products: readonly Product[], text: string, limit = 8): Product[] {
  const key = searchKey(text);
  if (key.replace(/ /g, '').length < MIN_PICK_LETTERS) {
    return [];
  }
  const words = key.split(' ');
  const found = products.filter((product) => {
    const name = searchKey(product.name);
    return words.every((word) => name.includes(word));
  });
  const starts = (product: Product) => (searchKey(product.name).startsWith(words[0]) ? 0 : 1);
  return [...found].sort((a, b) => starts(a) - starts(b)).slice(0, limit);
}
