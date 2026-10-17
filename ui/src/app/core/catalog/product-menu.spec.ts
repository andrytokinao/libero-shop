import { Product } from '../models';
import { productMenu, requestFor } from './product-menu';

describe('product menu', () => {
  const rice: Product = {
    id: 7,
    name: 'Riz',
    price: 900,
    stockQuantity: 40,
    unit: 'sac',
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 36000,
  };
  const everything = { canAddStock: true, canAdjustStock: true, canSeeHistory: true, canEdit: true };

  it('opens a count as a correction on the inventory', () => {
    expect(requestFor('count', rice)).toEqual({ action: 'adjust', product: rice, count: true });
  });

  it('opens "Seuil d’alerte" on the sheet, the cursor in the threshold', () => {
    expect(requestFor('threshold', rice)).toEqual({ action: 'edit', product: rice, tab: 'fiche', focus: 'threshold' });
  });

  it('starts a duplicate from the product it copies', () => {
    expect(requestFor('duplicate', rice)).toEqual({ action: 'create', copyOf: rice });
  });

  it('gives a reader of the stock no entry at all', () => {
    const none = { canAddStock: false, canAdjustStock: false, canSeeHistory: false, canEdit: false };
    expect(productMenu(rice, none)).toEqual([]);
  });

  it('marks only the removal in red', () => {
    const red = productMenu(rice, everything).filter((entry) => entry.danger);
    expect(red.map((entry) => entry.label)).toEqual(['Retirer de la vente']);
  });
});
