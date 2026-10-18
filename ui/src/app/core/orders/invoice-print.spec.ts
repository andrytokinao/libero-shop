import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Invoice } from '../models';
import { InvoiceStore } from '../store/invoice.store';
import { RemittanceStore } from '../store/remittance.store';
import { showPdf } from '../utils/pdf.util';
import { InvoiceActionRunner } from './invoice-action-runner.service';

describe('printing an invoice', () => {
  const invoice = { id: 7, invoiceNumber: 'F-1007' } as Invoice;
  const pdf = new Blob(['%PDF-1.4'], { type: 'application/pdf' });

  it('opens the PDF the server laid out, then marks the invoice printed', () => {
    const calls: string[] = [];
    const store = {
      pdf: jasmine.createSpy('pdf').and.callFake(() => {
        calls.push('pdf');
        return of(pdf);
      }),
      print: jasmine.createSpy('print').and.callFake(() => {
        calls.push('print');
        return of(invoice);
      }),
    };
    const open = spyOn(window, 'open').and.callFake(() => {
      calls.push('open');
      return {} as Window;
    });
    TestBed.configureTestingModule({
      providers: [
        { provide: InvoiceStore, useValue: store },
        { provide: RemittanceStore, useValue: {} },
      ],
    });

    let message = '';
    TestBed.inject(InvoiceActionRunner)
      .print(invoice)
      .subscribe((text) => (message = text));

    expect(calls).toEqual(['pdf', 'open', 'print']);
    expect(open.calls.mostRecent().args[1]).toBe('_blank');
    expect(message).toContain('F-1007');
  });

  it('saves the PDF as a file when the browser refuses the new tab', () => {
    spyOn(window, 'open').and.returnValue(null);
    const click = spyOn(HTMLAnchorElement.prototype, 'click');

    showPdf(pdf, 'F-1007.pdf');

    expect(click).toHaveBeenCalled();
    expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe('F-1007.pdf');
  });
});
