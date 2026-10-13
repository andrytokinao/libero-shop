import { TestBed } from '@angular/core/testing';
import { CATALOG_VIEW_STORAGE_KEY, CatalogViewService } from './catalog-view.service';

describe('CatalogViewService', () => {
  afterEach(() => localStorage.removeItem(CATALOG_VIEW_STORAGE_KEY));

  it('shows tiles when nothing was chosen', () => {
    localStorage.removeItem(CATALOG_VIEW_STORAGE_KEY);

    expect(TestBed.inject(CatalogViewService).view()).toBe('grid');
  });

  it('starts from the choice kept on this device', () => {
    localStorage.setItem(CATALOG_VIEW_STORAGE_KEY, 'list');

    expect(TestBed.inject(CatalogViewService).view()).toBe('list');
  });

  it('applies a choice at once and keeps it', () => {
    const service = TestBed.inject(CatalogViewService);

    service.choose('list');

    expect(service.view()).toBe('list');
    expect(localStorage.getItem(CATALOG_VIEW_STORAGE_KEY)).toBe('list');
  });
});
