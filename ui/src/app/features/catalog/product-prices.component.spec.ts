import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { Product, ProductCost, RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductPricesComponent } from './product-prices.component';

describe('ProductPricesComponent', () => {
  const rice: Product = {
    id: 7,
    name: 'Riz vrac',
    price: 900,
    stockQuantity: 7000,
    unit: 'kapoka',
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 0,
    packagings: [],
  };
  const salt: Product = { ...rice, id: 8, name: 'Sel', unit: null, barcode: '6001007000007' };

  const costs: ProductCost[] = [
    {
      productId: 7,
      name: 'Riz vrac',
      unit: 'kapoka',
      salePrice: 900,
      stockQuantity: 7000,
      averageCost: 785.71,
      unitMargin: 114.29,
      marginRate: 12.7,
      lastUnitCost: null,
      lastSupplierName: null,
      lastPurchaseDate: null,
      purchaseUnitId: 40,
      units: [
        { packagingId: null, label: 'kapoka', factor: 1, salePrice: 900, cost: 785.71, margin: 114.29, marginRate: 12.7, lastCost: null },
        { packagingId: 40, label: 'tonne', factor: 3500, salePrice: 2_700_000, cost: 2_750_000, margin: -50_000, marginRate: -1.9, lastCost: null },
      ],
    },
    {
      productId: 8,
      name: 'Sel',
      unit: null,
      salePrice: 500,
      stockQuantity: 10,
      averageCost: null,
      unitMargin: null,
      marginRate: null,
      lastUnitCost: null,
      lastSupplierName: null,
      lastPurchaseDate: null,
      purchaseUnitId: null,
      units: [
        { packagingId: null, label: null, factor: 1, salePrice: 500, cost: null, margin: null, marginRate: null, lastCost: null },
      ],
    },
  ];

  const roles = signal<RoleApp[]>([]);
  let fixture: ComponentFixture<ProductPricesComponent>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const rows = () => [...fixture.nativeElement.querySelectorAll('tbody tr')] as HTMLTableRowElement[];

  function render(...held: RoleApp[]): void {
    roles.set(held);
    TestBed.configureTestingModule({
      providers: [
        { provide: CostingApi, useValue: { productCosts: () => of(costs) } },
        { provide: CatalogApi, useValue: { products: () => of([rice, salt]) } },
        {
          provide: AuthService,
          useValue: { hasRole: (...wanted: RoleApp[]) => wanted.some((role) => roles().includes(role)) },
        },
      ],
    });
    fixture = TestBed.createComponent(ProductPricesComponent);
    fixture.detectChanges();
  }

  it('lists one row per unit, with its cost and profit, the purchase unit marked', () => {
    render(RoleApp.SUPER_ADMIN);

    expect(rows().length).toBe(3);
    const tonne = rows()[1];
    expect(tonne.textContent).toContain('tonne');
    expect(tonne.textContent).toContain('achat');
    expect(tonne.querySelector('.pp-loss')).not.toBeNull();
    expect(rows()[2].textContent).toContain('à saisir');
  });

  it('counts the products without a purchase price and those sold at a loss', () => {
    render(RoleApp.SUPER_ADMIN);

    const kpis = [...fixture.nativeElement.querySelectorAll('app-kpi-card .value')].map((v) =>
      (v as HTMLElement).textContent?.trim(),
    );
    expect(kpis).toEqual(['2', '1', '1']);
  });

  it('keeps only the products sold at a loss when asked', async () => {
    render(RoleApp.SUPER_ADMIN);
    fixture.componentInstance['filter'].set('loss');
    fixture.detectChanges();

    expect(text()).toContain('Riz vrac');
    expect(text()).not.toContain('Sel');
  });

  it('gives each product the same "⋮" as the stock screen, one per product', () => {
    render(RoleApp.SUPER_ADMIN);
    expect(fixture.nativeElement.querySelectorAll('.pp-actions app-product-menu').length).toBe(2);
  });

  it('offers to receive goods to the stock manager only', () => {
    render(RoleApp.SUPER_ADMIN);
    expect(menuOfFirstProduct()).not.toContain('Ajouter du stock');

    TestBed.resetTestingModule();
    render(RoleApp.DEPOT_MANAGER);
    expect(menuOfFirstProduct()).toContain('Ajouter du stock');
  });

  it('shows alone the product a scanned code designates', () => {
    render(RoleApp.SUPER_ADMIN);
    fixture.componentInstance['search'].set('6001007000007');
    fixture.detectChanges();

    expect(text()).toContain('Sel');
    expect(text()).not.toContain('Riz vrac');
  });

  /** Opens the first product's "⋮" and reads its entries. */
  function menuOfFirstProduct(): string[] {
    (fixture.nativeElement.querySelector('.pp-actions .am-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    return [...fixture.nativeElement.querySelectorAll('[role=menuitem]')].map(
      (entry) => (entry as HTMLElement).querySelector('span')?.textContent?.trim() ?? '',
    );
  }
});
