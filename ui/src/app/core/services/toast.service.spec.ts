import { ToastService } from './toast.service';

describe('ToastService', () => {
  it('runs a button of the message and takes the message away', () => {
    const toasts = new ToastService();
    let opened = '';
    toasts.show('Produit « Riz » créé.', [{ label: 'Ajouter du stock', run: () => (opened = 'stock') }]);

    toasts.act(toasts.actions()[0]);

    expect(opened).toBe('stock');
    expect(toasts.message()).toBeNull();
    expect(toasts.actions()).toEqual([]);
  });

  it('keeps a plain message without buttons', () => {
    const toasts = new ToastService();
    toasts.show('Enregistré.');
    expect(toasts.actions()).toEqual([]);
    toasts.dismiss();
  });
});
