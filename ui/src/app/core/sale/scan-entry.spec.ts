import { MAX_SCAN_QUANTITY, parseScanEntry } from './scan-entry';

describe('parseScanEntry', () => {
  it('reads a bare code as one unit', () => {
    expect(parseScanEntry('6111234567812')).toEqual({ quantity: 1, code: '6111234567812' });
  });

  it('reads a quantity before the star', () => {
    expect(parseScanEntry('3*6111234567812')).toEqual({ quantity: 3, code: '6111234567812' });
    expect(parseScanEntry(' 12 * baguette ')).toEqual({ quantity: 12, code: 'baguette' });
  });

  it('has nothing to look up while the code is still missing', () => {
    expect(parseScanEntry('')).toBeNull();
    expect(parseScanEntry('   ')).toBeNull();
    expect(parseScanEntry('3*')).toBeNull();
  });

  it('refuses a quantity no counter sells in one go', () => {
    expect(parseScanEntry('0*12')).toBeNull();
    expect(parseScanEntry(`${MAX_SCAN_QUANTITY + 1}*12`)).toBeNull();
  });

  it('leaves a star that is not after a number in the code', () => {
    expect(parseScanEntry('riz*1kg')).toEqual({ quantity: 1, code: 'riz*1kg' });
  });
});
