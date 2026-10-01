import { Product } from '../models';
import { Cart } from './cart';

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

  it('hands the sale endpoint ids and quantities, then empties', () => {
    cart.add(product(7, 10), 2);

    expect(cart.toSaleLines()).toEqual([{ productId: 7, quantity: 2 }]);
    cart.clear();
    expect(cart.isEmpty()).toBeTrue();
  });
});
