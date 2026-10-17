import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { BarcodeAssignment } from '../../core/catalog/barcode-assignment';
import { Product, ProductUnits } from '../../core/models';
import { BarcodesEditorComponent } from './barcodes-editor.component';

describe('BarcodesEditorComponent', () => {
  const oil: ProductUnits = {
    productId: 4,
    productName: 'Huile 1L',
    baseUnit: 'bouteille',
    basePrice: 6800,
    baseBarcode: '6001001000022',
    stockQuantity: 21,
    category: null,
    packagings: [{ id: 40, label: 'carton', factor: 12, price: 78000, barcode: null, reference: null, referenceQuantity: 12 }],
    purchaseUnitId: null,
    lowStockThreshold: null,
    defaultLowStockThreshold: 10,
    retired: false,
  };
  const rice = { id: 1, name: 'Riz 5kg' } as Product;

  let fixture: ComponentFixture<BarcodesEditorComponent>;
  let assigned: [number, number | null, string][];

  beforeEach(() => {
    assigned = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: CatalogApi,
          useValue: { productByCode: (code: string) => of(code === '9990001112223' ? rice : null) },
        },
        {
          provide: BarcodeAssignment,
          useValue: {
            assign: (productId: number, packagingId: number | null, code: string) => {
              assigned.push([productId, packagingId, code]);
              return of(oil);
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(BarcodesEditorComponent);
    fixture.componentRef.setInput('units', oil);
    fixture.detectChanges();
  });

  const field = (key: string) => fixture.nativeElement.querySelector(`#bc-${key}`) as HTMLInputElement;

  function type(key: string, code: string): void {
    field(key).value = code;
    field(key).dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('lists one line per unit, the base one first, with its code', () => {
    const units = Array.from(fixture.nativeElement.querySelectorAll('.bc-unit') as NodeListOf<HTMLElement>, (l) =>
      l.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(units).toEqual(['bouteille (base)', 'carton']);
    expect(field('base').value).toBe('6001001000022');
  });

  it('saves the code of the carton alone when the scanner presses Enter', () => {
    type('40', '6009009000099');
    field('40').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(assigned).toEqual([[4, 40, '6009009000099']]);
  });

  it('says at once that a code is another product’s, and does not send it', () => {
    type('40', '9990001112223');
    field('40').dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.bc-taken').textContent).toContain('Riz 5kg');
    field('40').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(assigned).toEqual([]);
  });

  it('selects the code of a line when the cursor enters it, so a scan replaces it', () => {
    document.body.appendChild(fixture.nativeElement);
    field('base').focus();

    expect(field('base').selectionStart).toBe(0);
    expect(field('base').selectionEnd).toBe('6001001000022'.length);
    fixture.nativeElement.remove();
  });
});
