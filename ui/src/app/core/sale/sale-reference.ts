import { CreateSaleRequest } from '../models';

/** A sale as it is about to be posted, before it is given its reference. */
export type SaleDraft = Omit<CreateSaleRequest, 'clientReference'>;

/**
 * The name a till gives a basket before posting it — the same on every attempt to post that
 * basket, so the server can tell a retry from a second sale.
 *
 * <p>The case it exists for: the sale is recorded, the answer is lost on the Wi-Fi, the screen
 * says the server did not answer and the cashier touches "Encaisser" again. Posted under the
 * same reference, the server hands back the invoice it already made instead of selling twice.
 *
 * <p>The reference belongs to the basket <em>as it is</em>: change a line, the customer or the
 * payment and the next post gets a new one. Reusing it there would be worse than no reference —
 * the server would answer with the first version of the sale, and the article just added would
 * leave the shop on no invoice at all.
 */
export class SaleReference {
  private basket: string | null = null;
  private reference = '';

  /** The reference to post this draft under: the last one if nothing changed, else a new one. */
  for(draft: SaleDraft): string {
    const basket = JSON.stringify(draft);
    if (basket !== this.basket) {
      this.basket = basket;
      this.reference = draw();
    }
    return this.reference;
  }

  /** The sale went through: the next basket is another sale, even with the very same goods. */
  renew(): void {
    this.basket = null;
  }
}

/**
 * 128 random bits, in hexadecimal. Not `crypto.randomUUID()`: it only exists in secure contexts,
 * and a till reached over the shop's plain-HTTP address is not one — `getRandomValues` is there
 * either way.
 */
function draw(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}
