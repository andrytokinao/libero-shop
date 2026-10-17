import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SEARCH_RELOAD_MS, debouncedAction } from './debounced-action';

describe('debouncedAction', () => {
  it('six frappes rapprochées ne demandent qu une fois, après la pause', fakeAsync(() => {
    let calls = 0;
    const reload = TestBed.runInInjectionContext(() => debouncedAction(() => calls++));

    for (let i = 0; i < 6; i++) {
      reload();
      tick(100);
    }
    expect(calls).toBe(0);

    tick(SEARCH_RELOAD_MS);
    expect(calls).toBe(1);
  }));

  it('deux recherches séparées par une pause font deux demandes', fakeAsync(() => {
    let calls = 0;
    const reload = TestBed.runInInjectionContext(() => debouncedAction(() => calls++));

    reload();
    tick(SEARCH_RELOAD_MS);
    reload();
    tick(SEARCH_RELOAD_MS);

    expect(calls).toBe(2);
  }));
});
