import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { StockApi } from '../../core/api/stock.api';
import { Supplier, Supply } from '../../core/models';
import { SupplierPurchasesDialogComponent } from './supplier-purchases-dialog.component';

describe('SupplierPurchasesDialogComponent', () => {
  const supplier = { id: 2, name: 'Distri Lait SARL' } as Supplier;
  const supplies = [
    {
      id: 1,
      unitLabel: 'carton',
      movementDate: '2026-10-17T09:00:00',
      product: { id: 3, name: 'Lait en poudre 400g', unit: null },
      supplier: { id: 2, name: 'Distri Lait SARL' },
      unitCost: 85200,
      cancelledAt: null,
    },
    {
      id: 2,
      unitLabel: null,
      movementDate: '2026-10-13T09:00:00',
      product: { id: 3, name: 'Lait en poudre 400g', unit: null },
      supplier: { id: 2, name: 'Distri Lait SARL' },
      unitCost: 7100,
      cancelledAt: null,
    },
  ] as unknown as Supply[];

  let fixture: ComponentFixture<SupplierPurchasesDialogComponent>;

  it('asks the server for this supplier only, and lists one line per product and unit', () => {
    const suppliesOf = jasmine.createSpy('suppliesOf').and.returnValue(of(supplies));
    TestBed.configureTestingModule({ providers: [{ provide: StockApi, useValue: { suppliesOf } }] });
    fixture = TestBed.createComponent(SupplierPurchasesDialogComponent);
    fixture.componentRef.setInput('supplier', supplier);
    fixture.detectChanges();

    const rows = Array.from(fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>, (r) =>
      r.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(suppliesOf).toHaveBeenCalledWith(2);
    expect(rows.length).toBe(2);
    expect(rows[0]).toContain('unité');
    expect(rows[1]).toContain('carton');
  });
});
