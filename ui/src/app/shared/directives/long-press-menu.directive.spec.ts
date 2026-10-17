import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LONG_PRESS_MS, LongPressMenuDirective } from './long-press-menu.directive';

@Component({
  standalone: true,
  imports: [LongPressMenuDirective],
  template: `
    <div class="row" appLongPressMenu>
      <button class="name" type="button" (click)="opened = opened + 1">Riz</button>
      <button class="am-trigger" type="button" [attr.aria-expanded]="menu" (click)="menu = !menu">⋮</button>
    </div>
  `,
})
class RowComponent {
  opened = 0;
  menu = false;
}

function touch(target: Element, type: string, x = 10, y = 10): void {
  const event = new Event(type, { bubbles: true }) as Event & { touches: { clientX: number; clientY: number }[] };
  Object.defineProperty(event, 'touches', { value: [{ clientX: x, clientY: y }] });
  target.dispatchEvent(event);
}

describe('LongPressMenuDirective', () => {
  let fixture: ComponentFixture<RowComponent>;
  let row: HTMLElement;
  let name: HTMLButtonElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(RowComponent);
    fixture.detectChanges();
    row = fixture.nativeElement.querySelector('.row');
    name = fixture.nativeElement.querySelector('.name');
  });

  it('un appui long ouvre le « ⋮ » de la ligne, et le tap qui suit n ouvre pas la ligne', fakeAsync(() => {
    touch(name, 'touchstart');
    tick(LONG_PRESS_MS);
    expect(fixture.componentInstance.menu).toBeTrue();

    touch(name, 'touchend');
    name.click();
    expect(fixture.componentInstance.opened).toBe(0);
    tick(400);
  }));

  it('le doigt relâché ne touche pas l entrée du menu apparue dessous', fakeAsync(() => {
    touch(name, 'touchstart');
    tick(LONG_PRESS_MS);
    touch(name, 'touchend');
    // The click of the lifted finger, landing on what the menu put there.
    (fixture.nativeElement.querySelector('.am-trigger') as HTMLButtonElement).click();
    expect(fixture.componentInstance.menu).toBeTrue();
    tick(400);
  }));

  it('un doigt qui glisse fait défiler : pas de menu', fakeAsync(() => {
    touch(name, 'touchstart');
    touch(name, 'touchmove', 10, 60);
    tick(LONG_PRESS_MS);
    expect(fixture.componentInstance.menu).toBeFalse();
  }));

  it('un appui court reste un tap sur la ligne', fakeAsync(() => {
    touch(name, 'touchstart');
    tick(100);
    touch(name, 'touchend');
    name.click();
    expect(fixture.componentInstance.opened).toBe(1);
    expect(fixture.componentInstance.menu).toBeFalse();
  }));

  it('un clic droit ouvre le menu au lieu de celui du navigateur', () => {
    const event = new MouseEvent('contextmenu', { bubbles: true, cancelable: true });
    row.dispatchEvent(event);
    expect(event.defaultPrevented).toBeTrue();
    expect(fixture.componentInstance.menu).toBeTrue();
  });
});
