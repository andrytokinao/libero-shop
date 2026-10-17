import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { MarginReport, Product, RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { MarginComponent } from './margin.component';

describe('MarginComponent', () => {
  const salt: Product = {
    id: 8,
    name: 'Sel',
    price: 500,
    stockQuantity: 10,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 0,
    packagings: [],
  };

  const report: MarginReport = {
    from: '2026-10-01',
    to: '2026-10-17',
    revenue: 5000,
    paidRevenue: 5000,
    costedRevenue: 0,
    costOfGoodsSold: 0,
    grossMargin: 0,
    marginRate: null,
    uncostedRevenue: 5000,
    coveragePercent: 0,
    byProduct: [
      {
        productId: 8,
        name: 'Sel',
        unitsSold: 10,
        revenue: 5000,
        costOfGoodsSold: 0,
        grossMargin: 0,
        marginRate: null,
        fullyCosted: false,
      },
    ],
  };

  const roles = signal<RoleApp[]>([]);
  let fixture: ComponentFixture<MarginComponent>;

  async function render(...held: RoleApp[]): Promise<void> {
    roles.set(held);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CostingApi,
          useValue: {
            margin: () => of(report),
            stockValuation: () => of(null),
            productCost: () => of(null),
          },
        },
        { provide: CatalogApi, useValue: { products: () => of([salt]) } },
        {
          provide: AuthService,
          useValue: { hasRole: (...wanted: RoleApp[]) => wanted.some((role) => roles().includes(role)) },
        },
      ],
    });
    fixture = TestBed.createComponent(MarginComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 400));
    fixture.detectChanges();
  }

  const badgeButton = () => fixture.nativeElement.querySelector('button.badge-btn') as HTMLButtonElement | null;

  it('turns "prix d’achat inconnu" into a way to fill it in, for whoever may write the catalogue', async () => {
    await render(RoleApp.SUPER_ADMIN);

    expect(badgeButton()?.textContent).toContain("prix d'achat inconnu");
    badgeButton()!.click();
    fixture.detectChanges();

    expect(fixture.componentInstance['asked']()).toEqual({ action: 'edit', product: salt, focus: 'cost' });
  });

  it('only says "partiel" to an account that cannot change the price', async () => {
    await render(RoleApp.CASHIER);

    expect(badgeButton()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('partiel');
  });
});
