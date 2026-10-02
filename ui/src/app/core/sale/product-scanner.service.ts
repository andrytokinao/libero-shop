import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product } from '../models';
import { Cart } from './cart';
import { SaleUnit, baseUnitOf, formatAmount, formatQuantity, unitByBarcode } from './sale-unit';
import { ScanEntry } from './scan-entry';

/** What became of one entry — the screen decides how to show it, the scanner only reports. */
export type ScanOutcome =
  | {
      readonly kind: 'added';
      readonly product: Product;
      readonly added: number;
      readonly asked: number;
      /** The unit it went in as: a sack's own barcode puts in a sack. */
      readonly unit: SaleUnit;
    }
  | {
      readonly kind: 'out-of-stock';
      readonly product: Product;
      /** The unit asked for: a shelf can hold some sugar and still not a whole sack. */
      readonly unit: SaleUnit;
    }
  | { readonly kind: 'ambiguous'; readonly code: string; readonly matches: number }
  | { readonly kind: 'unknown'; readonly code: string };

/**
 * Turns a validated entry into a cart line: finds the product its code designates and puts the
 * asked quantity in the cart, as far as the stock allows.
 *
 * <p>The code is looked up on the server, not in whatever list the screen holds: a till must
 * find any article of the shop, including the thousands not on screen. Only when no product
 * carries the code does the screen's own search get a say — through `matches`, what it
 * narrowed the same text down to. One match is taken, so that typing "baguette" then Enter
 * works as well as a scan; several are left for the cashier to pick. An identity always wins
 * over a guess.
 */
@Injectable({ providedIn: 'root' })
export class ProductScanner {
  private readonly catalog = inject(CatalogApi);

  scan(entry: ScanEntry, cart: Cart, matches: readonly Product[] = []): Observable<ScanOutcome> {
    return this.catalog.productByCode(entry.code).pipe(
      map((found): ScanOutcome => {
        const product = found ?? (matches.length === 1 ? matches[0] : null);
        if (!product) {
          return matches.length > 1
            ? { kind: 'ambiguous', code: entry.code, matches: matches.length }
            : { kind: 'unknown', code: entry.code };
        }
        // A code is an identity: the sack's own code sells a sack, the product's own code its
        // base unit. A name typed then Enter adds in the unit the product's line is already in.
        const unit =
          unitByBarcode(product, entry.code) ??
          (entry.code === product.barcode ? baseUnitOf(product) : cart.currentUnitOf(product));
        const added = cart.add(product, entry.quantity, unit);
        return added > 0
          ? { kind: 'added', product, added, asked: entry.quantity, unit }
          : { kind: 'out-of-stock', product, unit };
      }),
    );
  }
}

/** The outcome in the words a cashier reads, the same on every screen that sells. */
export function describeScan(outcome: ScanOutcome): string {
  switch (outcome.kind) {
    case 'added':
      return outcome.added < outcome.asked
        ? `${outcome.product.name} : ${formatAmount(outcome.added, outcome.unit.label)} ajouté(s) sur ${formatQuantity(outcome.asked)} — stock insuffisant.`
        : `${outcome.product.name} × ${formatAmount(outcome.added, outcome.unit.label)} ajouté.`;
    case 'out-of-stock':
      return outcome.unit.packagingId === null
        ? `${outcome.product.name} : plus de stock disponible.`
        : `${outcome.product.name} : pas assez de stock pour un ${outcome.unit.label}.`;
    case 'ambiguous':
      return `${outcome.matches} produits correspondent à « ${outcome.code} » — touchez le bon.`;
    case 'unknown':
      return `Aucun produit ne porte le code « ${outcome.code} ».`;
  }
}
