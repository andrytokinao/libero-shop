import { RemittanceStatus } from './enums';
import { UserApp } from './user-app.model';

/** Mirrors com.houssen.libertyshop.entity.CashRemittance (table cash_remittance). */
export interface CashRemittance {
  id: number;
  /** BigDecimal(12,2) on the backend. */
  amount: number;
  /** LocalDateTime serialized as ISO-8601. */
  remittanceDate: string;
  status: RemittanceStatus;
  submittedBy: UserApp;
  confirmedBy: UserApp | null;
}
