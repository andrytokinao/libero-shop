import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product } from '../models';
import { Cart } from './cart';
import { ProductEntry } from './product-entry';

function product(id: number, name: string): Product {
  return {
    id,
    name,
    price: 1000,
    stockQuantity: 10,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 10000,
  };
}

/** The field's behaviour around the scanner: when it empties, and what it may fall back on. */
describe('ProductEntry', () => {
  const riz = product(1, 'Riz');
  let cart: Cart;
  let shown: Product[];
  let entry: ProductEntry;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CatalogApi,
          useValue: {
            // No product carries any code: every entry falls back, or not, on the screen's list.
            productByCode: (): Observable<Product | null> => of(null),
          },
        },
      ],
    });
    cart = new Cart();
    shown = [riz];
    entry = TestBed.runInInjectionContext(() => new ProductEntry(cart, () => shown));
  });

  it('adds the one product the typed text narrowed the list to, and empties the field', () => {
    entry.text.set('2*riz');
    entry.submit();

    expect(cart.quantityOf(riz)).toBe(2);
    expect(entry.text()).toBe('');
  });

  it('keeps the text when it named several products', () => {
    shown = [riz, product(2, 'Riz rouge')];
    entry.text.set('riz');
    entry.submit();

    expect(cart.isEmpty()).toBeTrue();
    expect(entry.text()).toBe('riz');
  });

  it('never lets a camera code fall back on what an unrelated word in the field narrowed', () => {
    entry.text.set('riz');
    entry.submit('6111234567890');

    expect(cart.isEmpty()).toBeTrue();
    expect(entry.text()).toBe('riz');
  });
});
