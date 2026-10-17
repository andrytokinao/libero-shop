import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { StockApi } from '../api/stock.api';
import { StockAdjustment, Supply } from '../models';
import { ToastService } from '../services/toast.service';
import { StockUndo } from './stock-undo';

describe('StockUndo', () => {
  let api: jasmine.SpyObj<StockApi>;
  let toasts: ToastService;
  let undo: StockUndo;

  const supply = (id: number) => ({ id }) as Supply;

  beforeEach(() => {
    api = jasmine.createSpyObj<StockApi>('StockApi', ['cancelSupply', 'cancelDelivery', 'cancelAdjustment']);
    api.cancelSupply.and.returnValue(of({} as StockAdjustment));
    api.cancelDelivery.and.returnValue(of([]));
    api.cancelAdjustment.and.returnValue(of({} as StockAdjustment));
    TestBed.configureTestingModule({ providers: [{ provide: StockApi, useValue: api }] });
    toasts = TestBed.inject(ToastService);
    undo = TestBed.inject(StockUndo);
  });

  afterEach(() => toasts.dismiss());

  it('le message d une réception porte « Annuler », qui écrit la réception inverse', () => {
    let undone = 0;
    undo.undone$.subscribe(() => undone++);
    undo.received('20 sacs de Riz ajoutés au stock.', [supply(7)]);

    expect(toasts.message()).toBe('20 sacs de Riz ajoutés au stock.');
    expect(toasts.actions().map((action) => action.label)).toEqual(['Annuler']);
    toasts.act(toasts.actions()[0]);

    expect(api.cancelSupply).toHaveBeenCalledWith(7);
    expect(toasts.message()).toBe('Réception annulée : le stock est revenu comme avant.');
    expect(undone).toBe(1);
  });

  it('un bon de plusieurs produits s annule en une fois, tout ou rien', () => {
    undo.received('Réception enregistrée : 2 produit(s).', [supply(3), supply(4)]);
    toasts.act(toasts.actions()[0]);

    expect(api.cancelDelivery).toHaveBeenCalledWith([3, 4]);
    expect(api.cancelSupply).not.toHaveBeenCalled();
    expect(toasts.message()).toContain('Réception de 2 produits annulée');
  });

  it('une correction s annule par la correction inverse', () => {
    undo.corrected('Stock de Riz corrigé : −3 sacs.', { id: 12 } as StockAdjustment);
    toasts.act(toasts.actions()[0]);

    expect(api.cancelAdjustment).toHaveBeenCalledWith(12);
    expect(toasts.message()).toBe('Correction annulée : le stock est revenu comme avant.');
  });
});
