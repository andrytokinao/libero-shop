import { Product } from './product.model';

/** What the server publishes on `/topic/stock` — mirrors StockChangePublisher.Change. */
export interface StockChange {
  /** The products whose stock moved. */
  productIds: number[];
  /** Those products as they now stand; empty when the server could not read them back. */
  products: Product[];
}
