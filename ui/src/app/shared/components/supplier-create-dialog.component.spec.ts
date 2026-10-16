import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Supplier } from '../../core/models';
import { SupplierCreateDialogComponent } from './supplier-create-dialog.component';

describe('SupplierCreateDialogComponent', () => {
  let fixture: ComponentFixture<SupplierCreateDialogComponent>;
  let http: HttpTestingController;
  let created: Supplier[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [SupplierCreateDialogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(SupplierCreateDialogComponent);
    http = TestBed.inject(HttpTestingController);
    created = [];
    fixture.componentInstance.created.subscribe((supplier) => created.push(supplier));
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function fill(id: string, value: string): void {
    const field: HTMLInputElement = fixture.nativeElement.querySelector(`#${id}`);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submitButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button[type=submit]');
  }

  it('waits for a name before it can be sent', () => {
    expect(submitButton().disabled).toBeTrue();
    fill('sn-name', 'SOCOLAIT');
    expect(submitButton().disabled).toBeFalse();
  });

  it('sends the name and an empty contact as none, and hands the new supplier over', () => {
    fill('sn-name', '  SOCOLAIT ');
    fill('sn-contact', '  ');
    submitButton().click();

    const request = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/suppliers'));
    expect(request.request.body).toEqual({ name: 'SOCOLAIT', contact: null });
    const supplier: Supplier = {
      id: 9,
      name: 'SOCOLAIT',
      contact: null,
      suppliedProducts: null,
      deliveryCount: 0,
      unitsReceived: 0,
    };
    request.flush(supplier);

    expect(created).toEqual([supplier]);
  });
});
