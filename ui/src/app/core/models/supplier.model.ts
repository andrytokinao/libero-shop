/** Mirrors com.houssen.libertyshop.entity.Supplier (table supplier). */
export interface Supplier {
  id: number;
  name: string;
  contact: string | null;
  /** Simple text today; could become a many-to-many with Product later. */
  suppliedProducts: string | null;
}
