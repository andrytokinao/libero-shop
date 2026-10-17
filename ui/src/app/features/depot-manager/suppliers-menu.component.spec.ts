import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { RoleApp, Supplier } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { SuppliersComponent } from './suppliers.component';

describe('SuppliersComponent menu', () => {
  const lait: Supplier = {
    id: 2,
    name: 'Distri Lait SARL',
    contact: null,
    suppliedProducts: null,
    deliveryCount: 3,
    unitsReceived: 40,
    retired: false,
  };
  const ancien: Supplier = { ...lait, id: 5, name: 'Ancien grossiste', retired: true };

  let fixture: ComponentFixture<SuppliersComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CatalogApi, useValue: { suppliers: () => of([lait, ancien]) } },
        { provide: AuthService, useValue: { hasRole: (...roles: RoleApp[]) => roles.includes(RoleApp.DEPOT_MANAGER) } },
      ],
    });
    fixture = TestBed.createComponent(SuppliersComponent);
    fixture.detectChanges();
  });

  function menuOf(row: number): string[] {
    (fixture.nativeElement.querySelectorAll('.am-trigger')[row] as HTMLButtonElement).click();
    fixture.detectChanges();
    return Array.from(fixture.nativeElement.querySelectorAll('[role=menuitem]') as NodeListOf<HTMLElement>, (e) =>
      e.textContent?.trim() ?? '',
    );
  }

  it('offers to receive from a supplier still offered, or to set it aside', () => {
    expect(menuOf(0)).toEqual(['Modifier', 'Ses prix', 'Nouvelle réception', 'Ne plus proposer']);
  });

  it('marks a supplier set aside, and offers it back instead of a receipt', () => {
    expect(fixture.nativeElement.textContent).toContain('Plus proposé');
    expect(menuOf(1)).toEqual(['Modifier', 'Ses prix', 'Proposer à nouveau']);
  });
});
