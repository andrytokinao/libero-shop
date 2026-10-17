import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { UnitModelApi } from '../../core/api/unit-model.api';
import { ProductCreateDialogComponent } from './product-create-dialog.component';

describe('ProductCreateDialogComponent', () => {
  let fixture: ComponentFixture<ProductCreateDialogComponent>;

  function render(barcode = ''): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CatalogApi, useValue: { categories: () => of([]) } },
        { provide: UnitModelApi, useValue: { models: () => of([]) } },
      ],
    });
    fixture = TestBed.createComponent(ProductCreateDialogComponent);
    fixture.componentRef.setInput('barcode', barcode);
    fixture.detectChanges();
  }

  function type(id: string, value: string): void {
    const field = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  const missing = () => (fixture.nativeElement.querySelector('.pn-missing') as HTMLElement | null)?.textContent?.trim();
  const more = () => fixture.nativeElement.querySelector('.pn-more') as HTMLDetailsElement;

  it('asks for the name, the price and the unit first, the rest folded', () => {
    render();
    expect(more().open).toBeFalse();
    expect(fixture.nativeElement.querySelector('#pn-name')).not.toBeNull();
  });

  it('says what the grey button waits for, until nothing is missing', async () => {
    render();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(missing()).toBe("Il manque : le nom et un prix de vente et l'unité de base (pièce, sac, kg…)");

    type('pn-name', 'Riz 25 kg');
    type('pn-price', '70000');
    expect(missing()).toBe("Il manque : l'unité de base (pièce, sac, kg…)");

    // "10" in stock must say 10 of what: the unit is asked rather than left to "unité".
    type('pn-unit', 'sac');
    expect(missing()).toBeUndefined();
  });

  it('unfolds the details when a scanned code is already in them', () => {
    render('6111234567812');
    expect(more().open).toBeTrue();
  });
});
