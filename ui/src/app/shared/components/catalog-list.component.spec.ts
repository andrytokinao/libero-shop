import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CatalogFolder, Product } from '../../core/models';
import { Cart } from '../../core/sale/cart';
import { CatalogListComponent } from './catalog-list.component';

function product(id: number, name: string, stockQuantity = 10): Product {
  return {
    id,
    name,
    price: 1000,
    stockQuantity,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 0,
  };
}

const RICE: Product = {
  ...product(3, 'Riz', 100),
  unit: 'kapoka',
  packagings: [
    { id: 9, label: 'kg', factor: 3.5, price: 3000, barcode: null, reference: null, referenceQuantity: 3.5 },
  ],
};

const FOLDER: CatalogFolder = {
  kind: 'folder',
  rayon: { id: 7, name: 'Boissons', parentId: null } as CatalogFolder['rayon'],
  folders: 0,
  products: 12,
};

describe('CatalogListComponent', () => {
  let fixture: ComponentFixture<CatalogListComponent>;
  let list: CatalogListComponent;
  let cart: Cart;
  const sugar = product(1, 'Sucre');
  const oil = product(2, 'Huile', 2);

  const element = (): HTMLElement => fixture.nativeElement;
  const rows = () => Array.from(element().querySelectorAll<HTMLElement>('.cl-row'));
  const main = (row: number) => rows()[row].querySelector<HTMLButtonElement>('.cl-main')!;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    cart = new Cart();
    fixture = TestBed.createComponent(CatalogListComponent);
    list = fixture.componentInstance;
    fixture.componentRef.setInput('products', [sugar, oil, RICE]);
    fixture.componentRef.setInput('folders', [FOLDER]);
    fixture.componentRef.setInput('cart', cart);
    fixture.detectChanges();
  });

  it('shows a line per product and the rayons as chips', () => {
    expect(rows().map((row) => row.querySelector('.cl-name')?.textContent)).toEqual(['Sucre', 'Huile', 'Riz']);
    expect(element().querySelector('.cl-folder')?.textContent).toContain('Boissons');
  });

  it('adds one on a tap, then counts and takes back on the line', () => {
    main(0).click();
    main(0).click();
    fixture.detectChanges();

    expect(cart.quantityOf(sugar)).toBe(2);
    expect(rows()[0].querySelector('.cl-count')?.textContent).toBe('2');

    rows()[0].querySelector<HTMLButtonElement>('.cl-step')!.click();
    expect(cart.quantityOf(sugar)).toBe(1);
  });

  it('opens the unit buttons of a product sold in several units instead of adding', () => {
    main(2).click();
    fixture.detectChanges();

    expect(cart.isEmpty()).toBeTrue();
    expect(rows()[2].querySelector('app-unit-picker')).not.toBeNull();
  });

  it('says which folder was opened', () => {
    const opened: CatalogFolder[] = [];
    list.folderOpened.subscribe((folder) => opened.push(folder));

    element().querySelector<HTMLButtonElement>('.cl-folder')!.click();

    expect(opened).toEqual([FOLDER]);
  });

  it('walks the lines with the arrows and stops at both ends', () => {
    expect(list.active()).toBeNull();

    list.move(1);
    expect(list.active()).toBe(sugar);
    list.move(-1);
    expect(list.active()).toBe(sugar);
    list.move(1);
    list.move(1);
    list.move(1);
    expect(list.active()).toBe(RICE);

    fixture.detectChanges();
    expect(rows().map((row) => row.classList.contains('active'))).toEqual([false, false, true]);
  });

  it('starts from the last line on an arrow up', () => {
    list.move(-1);

    expect(list.active()).toBe(RICE);
  });

  it('adds the highlighted line on Enter, as far as the shelf allows', () => {
    list.move(1);
    list.move(1);

    expect(list.pickActive(3)).toEqual({ product: oil, added: 2 });
    expect(cart.quantityOf(oil)).toBe(2);
  });

  it('leaves Enter to the field when no line is highlighted, or once the words changed', () => {
    expect(list.pickActive()).toBeNull();

    list.move(1);
    list.clearActive();

    expect(list.pickActive()).toBeNull();
    expect(cart.isEmpty()).toBeTrue();
  });

  it('forgets a highlighted line that left the list', () => {
    list.move(1);
    fixture.componentRef.setInput('products', [oil]);

    expect(list.active()).toBeNull();
  });
});
