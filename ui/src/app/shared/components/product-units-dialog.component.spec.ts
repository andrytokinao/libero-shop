import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { UnitModelApi } from '../../core/api/unit-model.api';
import { ProductUnits } from '../../core/models';
import { ProductUnitsDialogComponent } from './product-units-dialog.component';

describe('ProductUnitsDialogComponent', () => {
  const rice: ProductUnits = {
    productId: 1,
    productName: 'Riz 5kg',
    baseUnit: 'sac',
    basePrice: 12500,
    baseBarcode: '6001001000015',
    stockQuantity: 37,
    category: null,
    packagings: [],
    purchaseUnitId: null,
    lowStockThreshold: null,
    defaultLowStockThreshold: 10,
    retired: false,
  };

  let fixture: ComponentFixture<ProductUnitsDialogComponent>;
  let thresholds: (number | null)[];
  let retirements: boolean[];

  function render(tab?: 'fiche' | 'prix' | 'unites' | 'codes'): void {
    thresholds = [];
    retirements = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: CatalogApi,
          useValue: {
            productUnits: () => of(rice),
            categories: () => of([]),
            changeRetired: (_id: number, retired: boolean) => {
              retirements.push(retired);
              return of({ ...rice, retired });
            },
            changeAlertThreshold: (_id: number, threshold: number | null) => {
              thresholds.push(threshold);
              return of({ ...rice, lowStockThreshold: threshold });
            },
          },
        },
        { provide: CostingApi, useValue: { productCost: () => of(null) } },
        { provide: UnitModelApi, useValue: { models: () => of([]) } },
      ],
    });
    fixture = TestBed.createComponent(ProductUnitsDialogComponent);
    fixture.componentInstance.productId = 1;
    fixture.componentInstance.initialTab = tab;
    fixture.detectChanges();
  }

  /** Whether the field is on screen in the current tab. */
  function shown(id: string): boolean {
    const field = fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;
    return !field.closest('.un-off');
  }

  it('opens on the sheet: name shown, prices and units in their own tabs', () => {
    render();
    expect(shown('un-name')).toBeTrue();
    expect(shown('un-cost')).toBeFalse();
    expect(shown('un-label')).toBeFalse();
  });

  it('opens on the tab the menu asked for', () => {
    render('prix');
    expect(shown('un-cost')).toBeTrue();
    expect(shown('un-name')).toBeFalse();
  });

  it('keeps the barcode in the "Codes-barres" tab', () => {
    render('codes');
    expect(shown('un-base-barcode')).toBeTrue();
    expect(shown('un-base-price')).toBeFalse();
  });

  it('names the unit being added with one tap on a chip', () => {
    render('unites');
    const carton = Array.from(fixture.nativeElement.querySelectorAll('.un-chip') as NodeListOf<HTMLButtonElement>).find(
      (chip) => chip.textContent?.trim() === 'carton',
    )!;
    carton.click();
    fixture.detectChanges();

    expect(fixture.componentInstance['draft']().label).toBe('carton');
  });

  it('saves the alert threshold of this product from the sheet, a comma read as a point', () => {
    render();
    const field = fixture.nativeElement.querySelector('#un-alert') as HTMLInputElement;
    expect(field.placeholder).toContain('10');
    field.value = '2,5';
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.un-alert button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(thresholds).toEqual([2.5]);
    expect(fixture.nativeElement.querySelector('.un-alert').textContent).toContain('En alerte sous 2,5');
  });

  it('asks before taking the product off sale, then says it is off sale', () => {
    render();
    (fixture.nativeElement.querySelector('.un-sale button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(retirements).toEqual([]);

    const confirm = fixture.nativeElement.querySelectorAll('app-confirm-dialog .modal-foot button')[1] as HTMLButtonElement;
    confirm.click();
    fixture.detectChanges();

    expect(retirements).toEqual([true]);
    expect(fixture.nativeElement.querySelector('.un-sale').textContent).toContain('Retiré de la vente');
    expect(fixture.nativeElement.querySelector('app-confirm-dialog')).toBeNull();
  });
});
