import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { PackagingRequest, ProductUnits, UpdateProductRequest } from '../models';
import { BarcodeAssignment, codeTargetsOf } from './barcode-assignment';

describe('BarcodeAssignment', () => {
  const rice: ProductUnits = {
    productId: 7,
    productName: 'Riz 25 kg',
    baseUnit: 'kg',
    basePrice: 3000,
    baseBarcode: null,
    stockQuantity: 100,
    category: { id: 3, name: 'Épicerie' } as ProductUnits['category'],
    purchaseUnitId: 40,
    packagings: [
      { id: 40, label: 'sac', factor: 25, price: 70000, barcode: '111', reference: 'kg', referenceQuantity: 25 },
    ],
  };
  let sheet: UpdateProductRequest | null;
  let unit: { id: number; request: PackagingRequest } | null;
  let assignment: BarcodeAssignment;

  beforeEach(() => {
    sheet = null;
    unit = null;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CatalogApi,
          useValue: {
            productUnits: () => of(rice),
            updateProduct: (_: number, request: UpdateProductRequest) => {
              sheet = request;
              return of(rice);
            },
            updatePackaging: (_: number, id: number, request: PackagingRequest) => {
              unit = { id, request };
              return of(rice);
            },
          },
        },
      ],
    });
    assignment = TestBed.inject(BarcodeAssignment);
  });

  it('gives the code to the base unit and leaves the rest of the sheet as it is', () => {
    assignment.assign(7, null, ' 6111 ').subscribe();

    expect(sheet).toEqual({
      name: 'Riz 25 kg',
      price: 3000,
      barcode: '6111',
      categoryId: 3,
      purchaseCost: null,
      purchaseUnitId: 40,
    });
    expect(unit).toBeNull();
  });

  it('gives the code to a carton without changing what it holds or costs', () => {
    assignment.assign(7, 40, '6222').subscribe();

    expect(unit).toEqual({
      id: 40,
      request: { label: 'sac', factor: 25, price: 70000, barcode: '6222', reference: 'kg' },
    });
    expect(sheet).toBeNull();
  });

  it('lists the base unit first, then the other units, with the code each carries', () => {
    expect(codeTargetsOf(rice)).toEqual([
      { packagingId: null, label: 'kg', barcode: null },
      { packagingId: 40, label: 'sac', barcode: '111' },
    ]);
  });
});
