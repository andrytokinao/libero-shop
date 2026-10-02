import { inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProductStore } from '../store/product.store';
import { Cart } from './cart';

/**
 * Keeps a cart's stock figures current while the screen lives: when another till sells the last
 * bags of rice, the line asking for more turns short here before the sale is even sent — the
 * same way it would once the server had refused it. Must be called in an injection context.
 */
export function followStock(cart: Cart): void {
  inject(ProductStore)
    .pushed$.pipe(takeUntilDestroyed())
    .subscribe((products) =>
      cart.applyStock(
        products.map((product) => ({ productId: product.id, available: product.stockQuantity })),
      ),
    );
}
