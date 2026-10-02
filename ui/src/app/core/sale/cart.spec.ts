import { Product } from '../models';
import { Cart, CartLine } from './cart';
import { baseUnitOf, unitsOf } from './sale-unit';

function product(id: number, stockQuantity: number, price = 1000): Product {
  return {
    id,
    name: `Produit ${id}`,
    price,
    stockQuantity,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: price * stockQuantity,
  };
}

describe('Cart', () => {
  let cart: Cart;

  beforeEach(() => (cart = new Cart()));

  it('keeps lines in the order they were added, and sums them', () => {
    cart.add(product(2, 10, 500), 3);
    cart.add(product(1, 10, 2000));
    cart.add(product(2, 10, 500));

    expect(cart.lines().map((line) => [line.product.id, line.quantity])).toEqual([
      [2, 4],
      [1, 1],
    ]);
    expect(cart.units()).toBe(5);
    expect(cart.total()).toBe(4000);
  });

  it('never takes more than the shelf holds, and says how many it took', () => {
    const riz = product(1, 3);

    expect(cart.add(riz, 2)).toBe(2);
    expect(cart.add(riz, 5)).toBe(1);
    expect(cart.add(riz)).toBe(0);
    expect(cart.quantityOf(riz)).toBe(3);
    expect(cart.remainingStock(riz)).toBe(0);
  });

  it('does not open a line for an article out of stock', () => {
    expect(cart.add(product(1, 0))).toBe(0);
    expect(cart.isEmpty()).toBeTrue();
  });

  it('removes a line brought down to zero', () => {
    const riz = product(1, 10);
    cart.add(riz);
    cart.change(riz, -1);

    expect(cart.isEmpty()).toBeTrue();
  });

  it('knows the line added last, and puts back held lines in their order', () => {
    cart.add(product(1, 10));
    cart.add(product(2, 10));
    expect(cart.lastLine()?.product.id).toBe(2);

    const lines = cart.lines();
    cart.clear();
    expect(cart.lastLine()).toBeNull();

    cart.restore(lines);
    expect(cart.lines().map((line) => line.product.id)).toEqual([1, 2]);
  });

  it('turns a line short with the stock the server found, until it is brought down', () => {
    const eau = product(1, 10);
    const riz = product(2, 10);
    cart.add(eau, 5);
    cart.add(riz, 2);

    cart.applyStock([{ productId: 1, available: 3 }]);

    expect(cart.isShort(eau)).toBeTrue();
    expect(cart.isShort(riz)).toBeFalse();
    expect(cart.shortLines().map((line) => line.product.id)).toEqual([1]);
    expect(cart.quantityOf(eau)).toBe(5);
    expect(cart.remainingStock(cart.lines()[0].product)).toBe(0); // 3 - 5, never below 0

    // One "−" brings the line straight down to what the shelf holds.
    cart.change(cart.lines()[0].product, -1);
    expect(cart.quantityOf(eau)).toBe(3);
    expect(cart.hasShortage()).toBeFalse();
  });

  it('ignores stock news about products it does not hold', () => {
    cart.add(product(1, 10), 2);
    cart.applyStock([{ productId: 99, available: 0 }]);

    expect(cart.hasShortage()).toBeFalse();
    expect(cart.lines().length).toBe(1);
  });

  describe('with several units', () => {
    /** Loose rice counted in kapoka: 900 Ar the kapoka, 3 000 the kilo (3.5 kapoka). */
    function vrac(stock: number): Product {
      return {
        ...product(1, stock, 900),
        unit: 'kapoka',
        packagings: [{ id: 5, label: 'kg', factor: 3.5, price: 3000, barcode: null }],
      };
    }
    const kilo = unitsOf(vrac(0))[1];

    it('prices a line in its unit and takes its base units off the shelf', () => {
      const riz = vrac(100);
      cart.add(riz, 2, kilo);

      expect(cart.total()).toBe(6000);
      expect(cart.quantityOf(riz)).toBe(7);
      expect(cart.remainingStock(riz)).toBe(93);
      expect(cart.toSaleLines()).toEqual([{ productId: 1, packagingId: 5, quantity: 2 }]);
    });

    it('switches a line to another unit, keeping its number and its place', () => {
      const riz = vrac(100);
      cart.add(product(2, 10));
      cart.add(riz, 2);
      cart.add(product(3, 10));

      cart.setLineUnit(cart.lines()[1], kilo);

      expect(cart.lines().map((line) => line.product.id)).toEqual([2, 1, 3]);
      expect(cart.lines()[1].unit.label).toBe('kg');
      expect(cart.lines()[1].quantity).toBe(2);
    });

    it('joins the line already in the unit switched to', () => {
      const riz = vrac(100);
      cart.add(riz, 1, kilo);
      cart.add(riz, 3, baseUnitOf(riz));

      cart.setLineUnit(cart.lines()[1], kilo);

      expect(cart.lines().length).toBe(1);
      expect(cart.lines()[0].quantity).toBe(4);
    });

    it('shares one shelf between the units: what fits in kilos shrinks with the kapoka taken', () => {
      // 10 kapoka: 3 kapoka leave 7, which is exactly 2 kilos.
      const riz = vrac(10);
      cart.add(riz, 3, baseUnitOf(riz));

      expect(cart.add(riz, 5, kilo)).toBe(2);
      expect(cart.remainingStock(riz)).toBe(0);
    });

    it('takes half a kilo', () => {
      const riz = vrac(100);
      cart.add(riz, 0.5, kilo);

      expect(cart.quantityOf(riz)).toBe(1.75);
      expect(cart.total()).toBe(1500);
    });

    it('turns every line of a product short when the shelf cannot hold them together', () => {
      const riz = vrac(100);
      cart.add(riz, 10, kilo);
      cart.add(riz, 50, baseUnitOf(riz));

      cart.applyStock([{ productId: 1, available: 60 }]);

      expect(cart.shortLines().length).toBe(2);
    });

    it('labels the tile badge with the units only for a product sold in several', () => {
      const riz = vrac(100);
      cart.add(riz, 1, baseUnitOf(riz));
      cart.add(riz, 2, kilo);
      cart.add(product(2, 10), 3);

      expect(cart.countLabelOf(riz)).toBe('1 kapoka + 2 kg');
      expect(cart.countLabelOf(product(2, 10))).toBe('3');
    });

    it('reads a held line from before units as the base unit', () => {
      const riz = vrac(100);
      cart.restore([{ product: riz, quantity: 2 } as unknown as CartLine]);

      expect(cart.lines()[0].unit.packagingId).toBeNull();
      expect(cart.total()).toBe(1800);
    });
  });

  it('hands the sale endpoint ids and quantities, then empties', () => {
    cart.add(product(7, 10), 2);

    expect(cart.toSaleLines()).toEqual([{ productId: 7, packagingId: null, quantity: 2 }]);
    cart.clear();
    expect(cart.isEmpty()).toBeTrue();
  });
});
