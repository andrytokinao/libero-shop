import { HttpErrorResponse } from '@angular/common/http';
import { stockShortagesOf } from './stock-shortage';

describe('stockShortagesOf', () => {
  const shortage = { productId: 7, productName: 'Eau', requested: 5, available: 4 };

  function refusal(status: number, code: string, data?: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: { code, message: '…', timestamp: '', data } });
  }

  it('reads the short lines of a stock refusal', () => {
    expect(stockShortagesOf(refusal(409, 'INSUFFICIENT_STOCK', [shortage]))).toEqual([shortage]);
  });

  it('finds none in any other failure', () => {
    expect(stockShortagesOf(refusal(409, 'EMPTY_CART'))).toEqual([]);
    expect(stockShortagesOf(refusal(500, 'INSUFFICIENT_STOCK', [shortage]))).toEqual([]);
    expect(stockShortagesOf(refusal(409, 'INSUFFICIENT_STOCK', 'not a list'))).toEqual([]);
    expect(stockShortagesOf(new Error('network'))).toEqual([]);
  });
});
