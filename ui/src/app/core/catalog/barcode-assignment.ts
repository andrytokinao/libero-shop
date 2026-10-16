import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Packaging, ProductUnits } from '../models';

/** One unit of a product a code can be given to: the base unit (`packagingId` null) or another. */
export interface CodeTarget {
  readonly packagingId: number | null;
  readonly label: string;
  /** The code it carries now, replaced by the new one. */
  readonly barcode: string | null;
}

/** Every unit of a product, the base one first, as a list of places a code can go. */
export function codeTargetsOf(units: ProductUnits): CodeTarget[] {
  return [
    { packagingId: null, label: units.baseUnit ?? 'unité', barcode: units.baseBarcode },
    ...units.packagings.map((packaging) => ({
      packagingId: packaging.id,
      label: packaging.label,
      barcode: packaging.barcode,
    })),
  ];
}

/**
 * Gives a scanned code to one unit of an existing product — the base unit or a carton — through
 * the same writes as the configuration dialog, everything else of the product left as it is.
 *
 * <p>The server refuses a code already carried by another product; its message is the error
 * toast's to show.
 */
@Injectable({ providedIn: 'root' })
export class BarcodeAssignment {
  private readonly api = inject(CatalogApi);

  assign(productId: number, packagingId: number | null, code: string): Observable<ProductUnits> {
    return this.api
      .productUnits(productId)
      .pipe(switchMap((units) => this.write(units, packagingId, code.trim())));
  }

  private write(units: ProductUnits, packagingId: number | null, code: string): Observable<ProductUnits> {
    if (packagingId === null) {
      return this.api.updateProduct(units.productId, {
        name: units.productName,
        price: units.basePrice,
        barcode: code,
        categoryId: units.category?.id ?? null,
        // Null keeps the cost the receipts made: only the code changes.
        purchaseCost: null,
        purchaseUnitId: units.purchaseUnitId,
      });
    }
    const packaging = units.packagings.find((candidate) => candidate.id === packagingId);
    if (!packaging) {
      throw new Error(`Unité ${packagingId} absente du produit ${units.productId}`);
    }
    return this.api.updatePackaging(units.productId, packagingId, packagingWithCode(packaging, code));
  }
}

/** The unit as it is, its code replaced. */
function packagingWithCode(packaging: Packaging, code: string) {
  return {
    label: packaging.label,
    factor: packaging.referenceQuantity ?? packaging.factor,
    price: packaging.price,
    barcode: code,
    reference: packaging.reference,
  };
}
