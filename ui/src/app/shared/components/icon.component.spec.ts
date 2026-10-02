import { TestBed } from '@angular/core/testing';
import { ICONS } from '../../core/ui/icons';
import { IconComponent } from './icon.component';

describe('IconComponent', () => {
  function render(inputs: Record<string, unknown>): HTMLElement {
    const fixture = TestBed.createComponent(IconComponent);
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('draws every path of the named icon, at the size asked', () => {
    const svg = render({ name: 'cart', size: 24 }).querySelector('svg')!;

    expect(svg.querySelectorAll('path').length).toBe(ICONS.cart.length);
    expect(svg.getAttribute('width')).toBe('24');
  });

  it('is decorative unless given a label', () => {
    expect(render({ name: 'bell' }).querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');

    const labelled = render({ name: 'bell', label: 'Notifications' }).querySelector('svg')!;
    expect(labelled.getAttribute('aria-hidden')).toBeNull();
    expect(labelled.getAttribute('role')).toBe('img');
    expect(labelled.getAttribute('aria-label')).toBe('Notifications');
  });

  it('has every icon drawn with at least one path', () => {
    Object.entries(ICONS).forEach(([name, paths]) => expect(paths.length).withContext(name).toBeGreaterThan(0));
  });
});
