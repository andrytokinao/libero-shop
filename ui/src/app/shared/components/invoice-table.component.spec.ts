import { TestBed } from '@angular/core/testing';
import { DeliveryStatus, Invoice, PaymentStatus, RoleApp } from '../../core/models';
import { InvoiceTableComponent } from './invoice-table.component';

const INVOICE: Invoice = {
  id: 7,
  invoiceNumber: 'F-1031',
  invoiceDate: '2026-09-25T14:22:00',
  clientName: 'Rina Rakoto',
  paymentStatus: PaymentStatus.UNPAID,
  deliveryStatus: DeliveryStatus.PENDING,
  printed: false,
  itemCount: 5,
  sale: {
    id: 7,
    saleDate: '2026-09-25T14:22:00',
    paymentStatus: PaymentStatus.UNPAID,
    totalAmount: 19000,
    seller: {
      id: 1,
      fullName: 'Fatima Randria',
      username: 'fatima',
      roles: [RoleApp.CASHIER],
      enabled: true,
    },
    lines: [
      {
        id: 1,
        quantity: 3,
        unitPrice: 5000,
        product: {
          id: 11,
          name: 'Riz Makalioka 5kg',
          price: 5000,
          stockQuantity: 40,
          barcode: null,
          category: null,
          lowStock: false,
          stockValue: 200000,
        },
      },
      {
        id: 2,
        quantity: 2,
        unitPrice: 2000,
        product: {
          id: 12,
          name: 'Huile Tiko 1L',
          price: 2000,
          stockQuantity: 12,
          barcode: null,
          category: null,
          lowStock: false,
          stockValue: 24000,
        },
      },
    ],
  },
};

describe('InvoiceTableComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InvoiceTableComponent] }).compileComponents();
  });

  function render(invoices: Invoice[] = [INVOICE]) {
    const fixture = TestBed.createComponent(InvoiceTableComponent);
    fixture.componentRef.setInput('invoices', invoices);
    fixture.detectChanges();
    return fixture;
  }

  it('opens the articles of a row in a dialog', () => {
    const fixture = render();

    expect(fixture.nativeElement.querySelector('.modal-backdrop')).toBeNull();

    (fixture.nativeElement.querySelector('.lnk-detail') as HTMLButtonElement).click();
    fixture.detectChanges();

    // One dialog, whichever rendering opened it — the table and the cards share it.
    const dialog = fixture.nativeElement.querySelector('.modal-backdrop') as HTMLElement;
    expect(fixture.nativeElement.querySelectorAll('.modal-backdrop').length).toBe(1);
    expect(dialog.textContent).toContain('F-1031');
    // The storekeeper checks the goods against product and quantity, not against a total.
    expect(dialog.textContent).toContain('Riz Makalioka 5kg');
    expect(dialog.textContent).toContain('3×');
    expect(dialog.textContent).toContain('Huile Tiko 1L');
  });

  it('closes the dialog again', () => {
    const fixture = render();

    (fixture.nativeElement.querySelector('.lnk-detail') as HTMLButtonElement).click();
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.modal-head .x') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.modal-backdrop')).toBeNull();
  });

  it('offers no detail when the dialog is turned off', () => {
    const fixture = render();
    fixture.componentRef.setInput('showDetail', false);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.lnk-detail')).toBeNull();
    expect(fixture.nativeElement.querySelector('.modal-backdrop')).toBeNull();
  });
});
