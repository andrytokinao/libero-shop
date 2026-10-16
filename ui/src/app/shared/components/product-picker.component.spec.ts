import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeMatch } from '../../core/catalog/product-pick';
import { Product } from '../../core/models';
import { ProductPickerComponent } from './product-picker.component';

const MILK: Product = {
  id: 3,
  name: 'Lait en poudre 400g',
  price: 9500,
  stockQuantity: 14,
  unit: null,
  barcode: '6001003000037',
  category: null,
  lowStock: false,
  stockValue: 0,
  packagings: [
    { id: 30, label: 'carton', factor: 12, price: 110000, barcode: '6009009000099', reference: null, referenceQuantity: 12 },
  ],
};
const RICE: Product = { ...MILK, id: 1, name: 'Riz 5kg', barcode: null, packagings: [] };

describe('ProductPickerComponent', () => {
  let fixture: ComponentFixture<ProductPickerComponent>;
  let input: HTMLInputElement;
  let picked: CodeMatch[];
  let unknown: string[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductPickerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ProductPickerComponent);
    fixture.componentRef.setInput('products', [RICE, MILK]);
    picked = [];
    unknown = [];
    fixture.componentInstance.picked.subscribe((match) => picked.push(match));
    fixture.componentInstance.unknownCode.subscribe((code) => unknown.push(code));
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('input');
  });

  function typeText(text: string): void {
    input.dispatchEvent(new Event('focus'));
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function press(key: string): void {
    input.dispatchEvent(new KeyboardEvent('keydown', { key }));
    fixture.detectChanges();
  }

  function proposals(): string[] {
    return [...fixture.nativeElement.querySelectorAll('.pp-item span')].map((span: Element) => span.textContent ?? '');
  }

  it('proposes nothing before the second letter', () => {
    typeText('l');
    expect(proposals()).toEqual([]);
  });

  it('proposes the products named so, with their stock', () => {
    typeText('lait');
    expect(proposals()).toEqual(['Lait en poudre 400g']);
    expect(fixture.nativeElement.querySelector('.pp-item small').textContent).toContain('stock');
  });

  it('picks the carton when its code is scanned', () => {
    typeText('6009009000099');
    press('Enter');
    expect(picked).toEqual([{ product: MILK, packagingId: 30 }]);
  });

  it('picks the highlighted proposal on Enter', () => {
    typeText('riz');
    press('Enter');
    expect(picked).toEqual([{ product: RICE, packagingId: null }]);
  });

  it('lets the next code replace the name just chosen instead of adding to it', async () => {
    document.body.appendChild(fixture.nativeElement);
    input.focus();
    typeText('riz');
    press('Enter');
    fixture.componentRef.setInput('selected', RICE);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));

    expect(input.value).toBe('Riz 5kg');
    expect(input.selectionStart).toBe(0);
    expect(input.selectionEnd).toBe('Riz 5kg'.length);
    fixture.nativeElement.remove();
  });

  it('says a scanned code is unknown instead of picking something else', () => {
    typeText('9990001112223');
    press('Enter');
    expect(picked).toEqual([]);
    expect(unknown).toEqual(['9990001112223']);
  });
});
