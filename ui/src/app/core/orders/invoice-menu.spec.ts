import { InvoiceAction, InvoiceActionKind } from './invoice-actions';
import { SHOW_DETAIL, invoiceMenu } from './invoice-menu';

describe('invoice-menu', () => {
  it('le détail d abord, puis les actions du détail, l annulation en rouge', () => {
    const actions: InvoiceAction[] = [
      { kind: InvoiceActionKind.PAY, label: 'Encaisser', tone: 'primary' },
      { kind: InvoiceActionKind.PRINT, label: 'Imprimer', tone: 'ghost' },
      { kind: InvoiceActionKind.CANCEL, label: 'Annuler la commande', tone: 'danger' },
    ];

    const menu = invoiceMenu(actions);

    expect(menu.map((entry) => entry.id)).toEqual([
      SHOW_DETAIL,
      InvoiceActionKind.PAY,
      InvoiceActionKind.PRINT,
      InvoiceActionKind.CANCEL,
    ]);
    expect(menu[3].danger).toBeTrue();
    expect(menu[1].danger).toBeUndefined();
  });

  it('sans action permise, le menu garde « Voir les articles »', () => {
    expect(invoiceMenu([]).map((entry) => entry.label)).toEqual(['Voir les articles']);
  });
});
