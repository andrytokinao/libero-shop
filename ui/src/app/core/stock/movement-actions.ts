import { StockAdjustment, Supply } from '../models';
import { formatAmount } from '../sale/sale-unit';

/** What can be done to a past receipt or correction from its "⋮". */
export type MovementAction = 'history' | 'print' | 'cancel';

/** One entry of that menu, as the shared action menu draws it. */
export interface MovementMenuEntry {
  readonly id: MovementAction;
  readonly label: string;
  readonly danger?: boolean;
}

/** A receipt's menu: the product's history, the printed note, and undoing it while it stands. */
export function supplyActions(supply: Supply, canCancel: boolean): MovementMenuEntry[] {
  return [
    { id: 'history', label: 'Historique du produit' },
    { id: 'print', label: 'Imprimer le bon' },
    ...(canCancel && !supply.cancelledAt
      ? [{ id: 'cancel' as const, label: 'Annuler cette réception', danger: true }]
      : []),
  ];
}

/** A correction's menu: a cancellation itself cannot be undone, nor anything twice. */
export function adjustmentActions(adjustment: StockAdjustment): MovementMenuEntry[] {
  return [
    { id: 'history', label: 'Historique du produit' },
    ...(!adjustment.cancelledAt && adjustment.reason !== 'CANCELLATION'
      ? [{ id: 'cancel' as const, label: 'Annuler cette correction', danger: true }]
      : []),
  ];
}

/** The receipt as a small page to print: product, quantity, price, supplier, who and when. */
export function receiptHtml(supply: Supply, shopName: string): string {
  const escape = (text: string) =>
    text.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c] ?? c);
  const when = new Date(supply.movementDate).toLocaleString('fr-FR');
  const quantity = formatAmount(supply.receivedQuantity, supply.unitLabel ?? supply.product.unit);
  const price = supply.unitCost === null ? '—' : `${formatAmount(supply.unitCost, null)} Ar`;
  const total = supply.totalCost === null ? '—' : `${formatAmount(supply.totalCost, null)} Ar`;
  return `<!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"><title>Bon de réception</title>
<style>body{font:14px sans-serif;margin:24px}h1{font-size:18px}td{padding:4px 12px 4px 0}</style></head><body>
<h1>${escape(shopName)} — bon de réception</h1>
<table>
<tr><td>Produit</td><td><strong>${escape(supply.product.name)}</strong></td></tr>
<tr><td>Quantité</td><td>${escape(quantity)}</td></tr>
<tr><td>Prix d'achat</td><td>${escape(price)}</td></tr>
<tr><td>Total</td><td>${escape(total)}</td></tr>
<tr><td>Fournisseur</td><td>${escape(supply.supplier?.name ?? 'Sans fournisseur')}</td></tr>
<tr><td>Reçu le</td><td>${escape(when)}</td></tr>
<tr><td>Par</td><td>${escape(supply.performedBy.fullName)}</td></tr>
</table>${supply.cancelledAt ? '<p><strong>Réception annulée.</strong></p>' : ''}
</body></html>`;
}
