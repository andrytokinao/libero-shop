import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FilterTab, FilterTabsComponent } from './filter-tabs.component';

describe('FilterTabsComponent', () => {
  const tabs: FilterTab<boolean>[] = [
    { value: true, label: 'À remettre', icon: '⏳', count: 3 },
    { value: false, label: 'Tous', icon: '≣' },
  ];
  let fixture: ComponentFixture<FilterTabsComponent<boolean>>;
  let chosen: boolean[];

  const buttons = () => [...fixture.nativeElement.querySelectorAll('[role=tab]')] as HTMLButtonElement[];

  beforeEach(() => {
    fixture = TestBed.createComponent(FilterTabsComponent<boolean>);
    fixture.componentRef.setInput('tabs', tabs);
    fixture.componentRef.setInput('value', true);
    chosen = [];
    fixture.componentInstance.value.subscribe((value) => chosen.push(value));
    fixture.detectChanges();
  });

  it('marks the current view and shows the count where there is one', () => {
    const [pending, all] = buttons();

    expect(pending.getAttribute('aria-selected')).toBe('true');
    expect(pending.querySelector('.count')?.textContent).toBe('3');
    expect(all.querySelector('.count')).toBeNull();
    expect(all.textContent).toContain('Tous');
  });

  it('shows no badge for nothing waiting', () => {
    fixture.componentRef.setInput('tabs', [{ ...tabs[0], count: 0 }, tabs[1]]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.count')).toBeNull();
  });

  it('switches on a click, and from the keyboard with the arrows', () => {
    buttons()[1].click();
    expect(chosen).toEqual([false]);

    fixture.detectChanges();
    buttons()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    expect(chosen).toEqual([false, true]);
  });
});
