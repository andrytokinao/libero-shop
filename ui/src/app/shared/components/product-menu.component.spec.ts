import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductDialogRequest } from '../../core/catalog/product-rights';
import { Product, RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductMenuComponent } from './product-menu.component';

describe('ProductMenuComponent', () => {
  const rice: Product = {
    id: 7,
    name: 'Riz',
    price: 900,
    stockQuantity: 40,
    unit: 'kapoka',
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 36000,
  };
  const roles = signal<RoleApp[]>([]);
  let fixture: ComponentFixture<ProductMenuComponent>;
  let asked: ProductDialogRequest[];

  const trigger = () => fixture.nativeElement.querySelector('.am-trigger') as HTMLButtonElement | null;
  const entries = () =>
    [...fixture.nativeElement.querySelectorAll('[role=menuitem]')] as HTMLButtonElement[];
  const labels = () => entries().map((entry) => entry.textContent?.trim());

  function open(...held: RoleApp[]): void {
    roles.set(held);
    fixture.detectChanges();
    trigger()?.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    roles.set([]);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { hasRole: (...wanted: RoleApp[]) => wanted.some((role) => roles().includes(role)) },
        },
      ],
    });
    fixture = TestBed.createComponent(ProductMenuComponent);
    fixture.componentRef.setInput('product', rice);
    asked = [];
    fixture.componentInstance.action.subscribe((request) => asked.push(request));
  });

  it('offers the stock manager everything, adding stock first', () => {
    open(RoleApp.DEPOT_MANAGER);

    expect(labels()).toEqual([
      'Ajouter du stock',
      'Configurer (prix, unités)',
      'Corriger le stock',
      'Historique du stock',
    ]);
  });

  it('keeps adding stock from the administrator, who may still edit and correct', () => {
    open(RoleApp.SUPER_ADMIN);

    expect(labels()).toEqual(['Configurer (prix, unités)', 'Corriger le stock', 'Historique du stock']);
  });

  it('shows no menu at all to the counter', () => {
    open(RoleApp.CASHIER);

    expect(trigger()).toBeNull();
  });

  it('says which action was picked on which product, and closes', () => {
    open(RoleApp.DEPOT_MANAGER);

    entries()[0].click();
    fixture.detectChanges();

    expect(asked).toEqual([{ action: 'addStock', product: rice }]);
    expect(entries()).toEqual([]);
  });

  it('closes on Escape without picking anything', () => {
    open(RoleApp.DEPOT_MANAGER);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(entries()).toEqual([]);
    expect(asked).toEqual([]);
  });
});
