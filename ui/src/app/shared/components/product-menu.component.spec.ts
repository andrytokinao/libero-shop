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
  /** The entry's name, without the grey hint on its right. */
  const labels = () => entries().map((entry) => entry.querySelector('span')?.textContent?.trim());
  const groups = () =>
    Array.from(fixture.nativeElement.querySelectorAll('.am-group') as NodeListOf<HTMLElement>, (g) => g.textContent?.trim());

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

  it('offers the stock manager everything, by family, adding stock first', () => {
    open(RoleApp.DEPOT_MANAGER);

    expect(labels()).toEqual([
      'Ajouter du stock',
      'Corriger le stock',
      'Compter ce produit',
      'Historique du stock',
      'Nom et rayon',
      "Prix de vente et d'entrée",
      'Unités et packaging',
      'Codes-barres',
      "Seuil d'alerte",
      'Dupliquer le produit',
      'Retirer de la vente',
    ]);
    expect(groups()).toEqual(['Stock', 'Configuration', 'Autres']);
  });

  it('keeps adding stock from the administrator, who may still edit and correct', () => {
    open(RoleApp.SUPER_ADMIN);

    expect(labels()).not.toContain('Ajouter du stock');
    expect(labels()).toContain('Corriger le stock');
    expect(labels()).toContain('Nom et rayon');
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

  it('shows the threshold of the product beside its entry, and offers to put a retired one back', () => {
    fixture.componentRef.setInput('product', { ...rice, lowStockThreshold: 50, retired: true });
    open(RoleApp.DEPOT_MANAGER);

    const threshold = entries().find((entry) => entry.textContent?.includes("Seuil d'alerte"))!;
    expect(threshold.querySelector('.am-hint')?.textContent?.trim()).toBe('50 kapoka');
    expect(labels()).toContain('Remettre en vente');
  });

  it('opens the configuration on the tab of the entry picked', () => {
    open(RoleApp.DEPOT_MANAGER);

    entries().find((entry) => entry.textContent?.includes('Codes-barres'))!.click();

    expect(asked).toEqual([{ action: 'edit', product: rice, tab: 'codes' }]);
  });

  it('closes on Escape without picking anything', () => {
    open(RoleApp.DEPOT_MANAGER);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(entries()).toEqual([]);
    expect(asked).toEqual([]);
  });
});
