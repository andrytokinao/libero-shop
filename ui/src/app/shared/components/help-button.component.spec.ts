import { TestBed } from '@angular/core/testing';
import { HELP_TEXTS } from '../../core/help/help-texts';
import { HelpButtonComponent } from './help-button.component';

describe('HelpButtonComponent', () => {
  it('« ? » déplie le texte et l exemple de la fenêtre, un clic ailleurs le replie', () => {
    const fixture = TestBed.createComponent(HelpButtonComponent);
    fixture.componentRef.setInput('topic', 'stock-adjust');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.hb-panel')).toBeNull();

    (host.querySelector('.hb-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.textContent).toContain(HELP_TEXTS['stock-adjust'].example);

    document.body.click();
    fixture.detectChanges();
    expect(host.querySelector('.hb-panel')).toBeNull();
  });

  it('chaque texte d aide a son exemple', () => {
    for (const help of Object.values(HELP_TEXTS)) {
      expect(help.text.length).toBeGreaterThan(40);
      expect(help.example).not.toBe('');
    }
  });
});
