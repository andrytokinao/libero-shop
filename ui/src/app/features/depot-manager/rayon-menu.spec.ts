import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { CategoryNode, Product } from '../../core/models';
import { CategoriesComponent } from './categories.component';
import { RayonProductsDialogComponent } from './rayon-products-dialog.component';

const boissons: CategoryNode = {
  id: 1,
  name: 'Boissons',
  parentId: null,
  depth: 0,
  path: 'Boissons',
  children: 1,
  imageVersion: null,
};
const eau: CategoryNode = { ...boissons, id: 2, name: 'Eau', parentId: 1, depth: 1, path: 'Boissons > Eau', children: 0 };
const vide: CategoryNode = { ...boissons, id: 3, name: 'Vide', children: 0 };
const vive = { id: 9, name: 'Eau Vive', category: eau, stockQuantity: 12, unit: null, packagings: [] } as unknown as Product;

describe('rayon menu', () => {
  let filed: [number, number[]][];

  beforeEach(() => {
    filed = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: CatalogApi,
          useValue: {
            categories: () => of([boissons, eau, vide]),
            stockByCategory: () => of([{ category: eau, path: eau.path, references: 1, units: 12, value: 0, sharePercent: 100 }]),
            products: () => of([vive]),
            fileProducts: (categoryId: number, ids: number[]) => {
              filed.push([categoryId, ids]);
              return of({ moved: ids.length });
            },
          },
        },
      ],
    });
  });

  function menuOf(fixture: ComponentFixture<CategoriesComponent>, row: number): string[] {
    (fixture.nativeElement.querySelectorAll('.am-trigger')[row] as HTMLButtonElement).click();
    fixture.detectChanges();
    return Array.from(fixture.nativeElement.querySelectorAll('[role=menuitem]') as NodeListOf<HTMLElement>, (e) =>
      e.textContent?.trim() ?? '',
    );
  }

  it('offers to delete a rayon only once it holds no sub-rayon, and to move only what it holds', () => {
    const fixture = TestBed.createComponent(CategoriesComponent);
    fixture.detectChanges();

    expect(menuOf(fixture, 0)).not.toContain('Supprimer');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    const eauMenu = menuOf(fixture, 1);
    expect(eauMenu).toContain('Déplacer ses produits vers…');
    expect(eauMenu).toContain('Supprimer');
  });

  it('files a product in another rayon from the list of the rayon', () => {
    const fixture = TestBed.createComponent(RayonProductsDialogComponent);
    fixture.componentRef.setInput('rayon', eau);
    fixture.componentRef.setInput('categories', [boissons, eau, vide]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Eau Vive');
    (fixture.componentInstance as unknown as { moveOne(p: Product, id: number): void }).moveOne(vive, 3);

    expect(filed).toEqual([[3, [9]]]);
  });
});
