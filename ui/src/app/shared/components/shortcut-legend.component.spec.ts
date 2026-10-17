import { TestBed } from '@angular/core/testing';
import { Shortcut } from '../../core/sale/shortcuts';
import { ShortcutLegendComponent } from './shortcut-legend.component';

describe('ShortcutLegendComponent', () => {
  let basketEmpty: boolean;
  const shortcuts: Shortcut[] = [
    { key: 'F2', label: 'Produit', run: () => undefined },
    { key: 'F4', label: 'Mettre en attente', run: () => undefined, enabled: () => !basketEmpty },
  ];

  function render() {
    const fixture = TestBed.createComponent(ShortcutLegendComponent);
    fixture.componentRef.setInput('shortcuts', shortcuts);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => (basketEmpty = true));

  it('fermée au départ, « ⌨ » ouvre la liste des touches', () => {
    const fixture = render();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.sl-panel')).toBeNull();

    (host.querySelector('.sl-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();

    const keys = Array.from(host.querySelectorAll('kbd')).map((key) => key.textContent?.trim());
    expect(keys).toEqual(['F2', 'F4']);
  });

  it('grise une touche qui ne peut rien faire maintenant', () => {
    const fixture = render();
    const host = fixture.nativeElement as HTMLElement;
    (host.querySelector('.sl-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();

    const rows = host.querySelectorAll('li');
    expect(rows[0].classList).not.toContain('off');
    expect(rows[1].classList).toContain('off');
  });

  it('un clic ailleurs la referme', () => {
    const fixture = render();
    const host = fixture.nativeElement as HTMLElement;
    (host.querySelector('.sl-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();

    document.body.click();
    fixture.detectChanges();

    expect(host.querySelector('.sl-panel')).toBeNull();
  });
});
