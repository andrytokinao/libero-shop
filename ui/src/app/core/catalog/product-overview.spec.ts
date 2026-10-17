import { ProductMovement } from '../models';
import { ProductMenuEntry } from './product-menu';
import { actionRows, recentReceipts } from './product-overview';

describe('product-overview', () => {
  const movement = (id: number, type: ProductMovement['type']) => ({ id, type }) as ProductMovement;

  it('ne garde que les trois dernières entrées, ventes et corrections écartées', () => {
    const history = [
      movement(9, 'OUTPUT'),
      movement(8, 'SUPPLY'),
      movement(7, 'ADJUSTMENT'),
      movement(6, 'SUPPLY'),
      movement(5, 'SUPPLY'),
      movement(4, 'SUPPLY'),
    ];

    expect(recentReceipts(history).map((receipt) => receipt.id)).toEqual([8, 6, 5]);
  });

  it('range les actions du menu en une rangée par famille, dans l ordre du menu', () => {
    const entries: ProductMenuEntry[] = [
      { id: 'addStock', label: 'Ajouter du stock', group: 'Stock' },
      { id: 'adjust', label: 'Corriger le stock', group: 'Stock' },
      { id: 'prices', label: 'Prix', group: 'Configuration' },
      { id: 'retire', label: 'Retirer de la vente', group: 'Autres', danger: true },
    ];

    const rows = actionRows(entries);

    expect(rows.map((row) => row.group)).toEqual(['Stock', 'Configuration', 'Autres']);
    expect(rows[0].entries.map((entry) => entry.id)).toEqual(['addStock', 'adjust']);
  });

  it('aucune action pour qui ne peut rien faire : aucune rangée', () => {
    expect(actionRows([])).toEqual([]);
  });
});
