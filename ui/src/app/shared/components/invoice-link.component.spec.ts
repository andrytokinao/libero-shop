import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DeliveryStatus, Invoice, PaymentStatus, RoleApp } from '../../core/models';
import { InvoiceLinkComponent } from './invoice-link.component';

const INVOICE: Invoice = {
  id: 31,
  invoiceNumber: 'FAC-2026-00031',
  invoiceDate: '2026-10-16T09:00:00',
  clientName: 'Client comptoir',
  paymentStatus: PaymentStatus.PAID,
  deliveryStatus: DeliveryStatus.DELIVERED,
  printed: true,
  itemCount: 0,
  sale: {
    id: 31,
    saleDate: '2026-10-16T09:00:00',
    paymentStatus: PaymentStatus.PAID,
    totalAmount: 12500,
    seller: {
      id: 1,
      fullName: 'Fatima Randria',
      username: 'fatima',
      roles: [RoleApp.CASHIER],
      enabled: true,
    },
    lines: [],
  },
  cancellation: null,
};

describe('InvoiceLinkComponent', () => {
  let fixture: ComponentFixture<InvoiceLinkComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [InvoiceLinkComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(InvoiceLinkComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('invoiceId', 31);
    fixture.componentRef.setInput('number', 'FAC-2026-00031');
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows the number and reads nothing before it is clicked', () => {
    expect(fixture.nativeElement.textContent).toContain('FAC-2026-00031');
    http.expectNone(() => true);
  });

  it('opens the invoice it names when clicked', () => {
    fixture.nativeElement.querySelector('button').click();
    const request = http.expectOne((r) => r.url.endsWith('/invoices/31'));
    request.flush(INVOICE);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-invoice-detail-dialog')).not.toBeNull();
  });
});
