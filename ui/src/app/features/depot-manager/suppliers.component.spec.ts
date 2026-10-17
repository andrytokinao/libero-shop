import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { SuppliersComponent } from './suppliers.component';

describe('SuppliersComponent', () => {
  let fixture: ComponentFixture<SuppliersComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CatalogApi, useValue: { suppliers: () => of([]) } },
      ],
    });
    fixture = TestBed.createComponent(SuppliersComponent);
    fixture.detectChanges();
  });

  const empty = () => fixture.nativeElement.querySelector('.empty') as HTMLElement | null;

  it('says the list is empty and offers both ways to fill it', () => {
    expect(empty()?.textContent).toContain("Aucun fournisseur pour l'instant.");
    const actions = Array.from(empty()!.querySelectorAll('.empty-actions button'), (b) => b.textContent?.trim());
    expect(actions[0]).toBe('＋ Nouveau fournisseur');
    expect(actions.length).toBe(2);
  });

  it('opens the new supplier form from the empty list', () => {
    (empty()!.querySelector('.empty-actions button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-supplier-create-dialog')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('le téléphone est facultatif');
  });
});
