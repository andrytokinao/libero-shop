import { ComponentFixture, TestBed } from '@angular/core/testing';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';
import { ConfirmDialogComponent } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let events: string[];

  beforeEach(async () => {
    fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Remettre à la caisse ?');
    fixture.componentRef.setInput('message', '14 000 Ar pour la facture F-1007.');
    fixture.componentRef.setInput('confirmLabel', 'Remettre 14 000 Ar');
    events = [];
    fixture.componentInstance.confirmed.subscribe(() => events.push('confirmed'));
    fixture.componentInstance.closed.subscribe(() => events.push('closed'));
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => fixture.nativeElement.remove());

  const buttons = () => fixture.nativeElement.querySelectorAll('.modal-foot button') as NodeListOf<HTMLButtonElement>;

  it('names what it does on its button, next to "Annuler"', () => {
    expect(buttons()[0].textContent?.trim()).toBe('Annuler');
    expect(buttons()[1].textContent?.trim()).toBe('Remettre 14 000 Ar');
  });

  it('confirms only from its own button', () => {
    buttons()[0].click();
    buttons()[1].click();
    expect(events).toEqual(['closed', 'confirmed']);
  });

  it('closes on Escape when it is the dialog on top', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(events).toEqual(['closed']);
  });

  it('leaves Escape to a dialog opened over it', () => {
    const over = document.createElement('div');
    over.innerHTML = '<div class="modal-backdrop"></div>';
    document.body.appendChild(over);

    expect(isTopmostDialog(fixture.nativeElement)).toBeFalse();
    expect(isTopmostDialog(over)).toBeTrue();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(events).toEqual([]);
    over.remove();
  });
});
