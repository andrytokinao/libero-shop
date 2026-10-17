import { CashRemittance, RemittanceStatus, RoleApp, ShopFeatures } from '../models';

/**
 * Whether this person's slips confirm themselves — mirrors RemittanceService.confirmsOwnSlip:
 * they hold the till, and the shop does not ask for a second person to count. Decides how the
 * buttons are worded; the server decides what actually happens, and says it in the slip.
 */
export function confirmsOwnSlip(roles: readonly RoleApp[], features: ShopFeatures): boolean {
  return roles.includes(RoleApp.CASHIER) && !features.dualControlRemittance;
}

/** The button that brings cash to the till, in the words of what it will do. */
export function remitLabel(selfConfirming: boolean): string {
  return selfConfirming ? 'Mettre en caisse' : 'Remettre à la caisse';
}

/** What became of the cash, from the slip the server answered — the truth, not the guess. */
export function describeSlip(slip: CashRemittance): string {
  const amount = `${slip.amount.toLocaleString('fr-FR')} Ar`;
  return slip.status === RemittanceStatus.CONFIRMED
    ? `${amount} mis en caisse (versement V-${slip.id}).`
    : `Versement V-${slip.id} de ${amount} déposé — en attente de confirmation par un caissier.`;
}

/** What the confirmation before handing cash over says, and what its button says. */
export interface RemitQuestion {
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
}

/**
 * The question asked before cash leaves the storekeeper's hands: the amount in full, and the
 * orders it is for — the same figures the cashier will count.
 *
 * @param invoiceNumbers the orders whose cash is handed over
 * @param amount         their total, in ariary
 */
export function remitQuestion(
  invoiceNumbers: readonly string[],
  amount: number,
  selfConfirming: boolean,
): RemitQuestion {
  const total = `${amount.toLocaleString('fr-FR')} Ar`;
  const orders =
    invoiceNumbers.length === 1
      ? `la facture ${invoiceNumbers[0]}`
      : `${invoiceNumbers.length} factures (${invoiceNumbers.join(', ')})`;
  return selfConfirming
    ? {
        title: 'Mettre en caisse ?',
        message: `${total} pour ${orders} vont dans la caisse.`,
        confirmLabel: `Mettre ${total} en caisse`,
      }
    : {
        title: 'Remettre à la caisse ?',
        message: `${total} pour ${orders} : un caissier comptera la somme et confirmera le versement.`,
        confirmLabel: `Remettre ${total}`,
      };
}
