import { MovementType } from './enums';
import { Invoice } from './invoice.model';
import { Product } from './product.model';
import { Supplier } from './supplier.model';
import { UserApp } from './user-app.model';

/**
 * Mirrors the abstract com.houssen.libertyshop.entity.StockMovement
 * (single table stock_movement, discriminator column movement_type).
 */
export interface StockMovement {
  id: number;
  movementType: MovementType;
  quantity: number;
  /** LocalDateTime serialized as ISO-8601. */
  movementDate: string;
  product: Product;
  performedBy: UserApp;
}

/** Mirrors com.houssen.libertyshop.entity.Supply — @DiscriminatorValue("SUPPLY"). */
export interface Supply extends StockMovement {
  movementType: MovementType.SUPPLY;
  supplier: Supplier;
}

/** Mirrors com.houssen.libertyshop.entity.StockOutput — @DiscriminatorValue("OUTPUT"). */
export interface StockOutput extends StockMovement {
  movementType: MovementType.OUTPUT;
  invoice: Invoice;
}

export const isSupply = (m: StockMovement): m is Supply => m.movementType === MovementType.SUPPLY;

export const isStockOutput = (m: StockMovement): m is StockOutput =>
  m.movementType === MovementType.OUTPUT;
