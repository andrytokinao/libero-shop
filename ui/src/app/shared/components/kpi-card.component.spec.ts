import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { KpiCardComponent } from './kpi-card.component';

describe('KpiCardComponent', () => {
  let fixture: ComponentFixture<KpiCardComponent>;
  let navigated: unknown[][];

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    navigated = [];
    spyOn(TestBed.inject(Router), 'navigate').and.callFake((...args: unknown[]) => {
      navigated.push(args);
      return Promise.resolve(true);
    });
    fixture = TestBed.createComponent(KpiCardComponent);
    fixture.componentRef.setInput('label', 'Références en alerte');
    fixture.componentRef.setInput('value', 4);
  });

  const host = () => fixture.nativeElement as HTMLElement;

  it('is a plain figure without a link', () => {
    fixture.detectChanges();
    host().click();
    expect(host().getAttribute('role')).toBeNull();
    expect(navigated).toEqual([]);
  });

  it('opens the list it counts, filtered, by click or by Enter', () => {
    fixture.componentRef.setInput('link', '/gestion-depot/stock');
    fixture.componentRef.setInput('linkQuery', { alertes: '1' });
    fixture.detectChanges();

    expect(host().getAttribute('role')).toBe('link');
    host().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(navigated).toEqual([[['/gestion-depot/stock'], { queryParams: { alertes: '1' } }]]);
  });

  it('lets the page apply its own filter when it only acts', () => {
    let opened = 0;
    fixture.componentRef.setInput('action', true);
    fixture.componentInstance.opened.subscribe(() => opened++);
    fixture.detectChanges();

    host().click();
    expect(opened).toBe(1);
    expect(navigated).toEqual([]);
  });
});
