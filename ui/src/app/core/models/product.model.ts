import { Category } from './category.model';

/** Mirrors com.houssen.libertyshop.entity.Product (table product). */
export interface Product {
  id: number;
  name: string;
  /** BigDecimal(12,2) on the backend. */
  price: number;
  stockQuantity: number;
  /** Unique, nullable — filled by hand today, by a barcode scanner tomorrow. */
  barcode: string | null;
  category: Category | null;
}
