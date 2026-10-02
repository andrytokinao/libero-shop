import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product } from '../models';
import { Cart } from './cart';
import { unitByBarcode } from './sale-unit';
import { ProductScanner, ScanOutcome, describeScan } from './product-scanner.service';

function product(id: number, stockQuantity: number): Product {
  return {
    id,
    name: `Produit ${id}`,
    price: 1000,
    stockQuantity,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 1000 * stockQuantity,
  };
}

/** The scanner's decisions, against a catalogue that knows a single code. */
describe('ProductScanner', () => {
  const riz = { ...product(1, 5), name: 'Riz', barcode: '611' };
  /** Loose rice in kapoka, whose 50 kg sack carries its own code. */
  const vrac: Product = {
    ...product(9, 1000),
    name: 'Riz vrac',
    unit: 'kapoka',
    barcode: 'VRAC',
    packagings: [{ id: 5, label: 'sac 50 kg', factor: 175, price: 145000, barcode: 'SAC' }],
  };
  let cart: Cart;
  let scanner: ProductScanner;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CatalogApi,
          useValue: {
            productByCode: (code: string): Observable<Product | null> =>
              of(code === riz.barcode ? riz : code === 'SAC' || code === 'VRAC' ? vrac : null),
          },
        },
      ],
    });
    scanner = TestBed.inject(ProductScanner);
    cart = new Cart();
  });

  function scan(code: string, quantity = 1, matches: Product[] = []): ScanOutcome {
    let outcome!: ScanOutcome;
    scanner.scan({ code, quantity }, cart, matches).subscribe((result) => (outcome = result));
    return outcome;
  }

  it('adds the product the code designates', () => {
    expect(scan('611', 2)).toEqual(jasmine.objectContaining({ kind: 'added', product: riz, added: 2, asked: 2 }));
    expect(cart.quantityOf(riz)).toBe(2);
  });

  it('prefers the code over what the screen happened to narrow the text to', () => {
    const other = product(2, 5);
    scan('611', 1, [other]);

    expect(cart.quantityOf(riz)).toBe(1);
    expect(cart.quantityOf(other)).toBe(0);
  });

  it('takes the screen\'s single match when no product carries the code', () => {
    const baguette = { ...product(2, 5), name: 'Baguette' };

    expect(scan('bag', 1, [baguette]).kind).toBe('added');
    expect(cart.quantityOf(baguette)).toBe(1);
  });

  it('leaves several matches to the cashier', () => {
    const outcome = scan('pro', 1, [product(2, 5), product(3, 5)]);

    expect(outcome).toEqual({ kind: 'ambiguous', code: 'pro', matches: 2 });
    expect(cart.isEmpty()).toBeTrue();
  });

  it('reports an unknown code', () => {
    expect(describeScan(scan('999'))).toBe('Aucun produit ne porte le code « 999 ».');
  });

  it('says when the stock fell short of the quantity asked', () => {
    const outcome = scan('611', 8);

    expect(outcome).toEqual(jasmine.objectContaining({ kind: 'added', product: riz, added: 5, asked: 8 }));
    expect(describeScan(outcome)).toContain('5 ajouté(s) sur 8');
    expect(scan('611').kind).toBe('out-of-stock');
  });

  it('sells the packaging whose own code was scanned, and says it in that unit', () => {
    const outcome = scan('SAC', 2);

    expect(cart.lines()[0].unit.label).toBe('sac 50 kg');
    expect(cart.quantityOf(vrac)).toBe(350);
    expect(describeScan(outcome)).toBe('Riz vrac × 2 sac 50 kg ajouté.');
  });

  it('sells the base unit for the product\'s own code, and a name in the unit already chosen', () => {
    scan('SAC');
    scan('VRAC');
    expect(cart.lines().map((line) => line.unit.label)).toEqual(['sac 50 kg', 'kapoka']);

    cart.clear();
    scan('SAC');
    // No product carries "riz": the screen's single match is taken, in the line's unit.
    scan('riz', 1, [vrac]);
    expect(cart.lines().length).toBe(1);
    expect(cart.lines()[0].quantity).toBe(2);
  });

  it('adds whole packagings only, and says when not even one fits', () => {
    const peu: Product = { ...vrac, stockQuantity: 200 };
    cart.add(peu, 1, unitByBarcode(peu, 'SAC')!);

    expect(cart.add(peu, 1, unitByBarcode(peu, 'SAC')!)).toBe(0);
    expect(cart.lines()[0].quantity).toBe(1);
    expect(describeScan({ kind: 'out-of-stock', product: peu, unit: unitByBarcode(peu, 'SAC')! })).toBe(
      'Riz vrac : pas assez de stock pour un sac 50 kg.',
    );
  });
});
