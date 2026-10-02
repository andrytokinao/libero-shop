import { signal } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RoleApp, UserRef } from '../../core/models';
import { UserPhotos } from '../../core/services/user-photos.service';
import { UserAvatarComponent } from './user-avatar.component';

/** The face, the name and the card on hover — against a fake photo cache. */
describe('UserAvatarComponent', () => {
  const fatima: UserRef = {
    id: 1,
    fullName: 'Fatima Randria',
    username: 'fatima',
    roles: [RoleApp.CASHIER, RoleApp.DEPOT_AGENT],
    enabled: true,
    photoVersion: null,
  };
  let asked: string[];
  let fixture: ComponentFixture<UserAvatarComponent>;
  let host: HTMLElement;

  function render(user: UserRef, inputs: Record<string, unknown> = {}): void {
    fixture = TestBed.createComponent(UserAvatarComponent);
    fixture.componentRef.setInput('user', user);
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    host = fixture.nativeElement;
  }

  beforeEach(() => {
    asked = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: UserPhotos,
          useValue: {
            url: (id: number, version: number) => {
              asked.push(`${id}:${version}`);
              return signal('blob:photo').asReadonly();
            },
          },
        },
      ],
    });
  });

  it('shows the initials on a colour of their own when there is no photo', () => {
    render(fatima);
    const face = host.querySelector('.face') as HTMLElement;

    expect(face.textContent?.trim()).toBe('FR');
    // The browser reports the hsl() colour as rgb(); what matters is that one is set.
    expect(face.style.background).toMatch(/rgb|hsl/);
    expect(face.getAttribute('aria-label')).toBe('Fatima Randria');
    expect(asked).toEqual([]);
  });

  it('shows the photo of the current version', () => {
    render({ ...fatima, photoVersion: 42 });

    expect(asked).toEqual(['1:42']);
    expect(host.querySelector('.face img')?.getAttribute('src')).toBe('blob:photo');
  });

  it('writes the name beside the face, greyed for a disabled account', () => {
    render({ ...fatima, enabled: false }, { showName: true, caption: 'Caisse' });

    expect(host.querySelector('.name')?.textContent).toContain('Fatima Randria');
    expect(host.querySelector('.name')?.classList).toContain('off');
    expect(host.querySelector('.caption')?.textContent).toContain('Caisse');
  });

  it('opens the card at once on keyboard focus, and closes it on Escape', () => {
    render(fatima);
    host.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();

    const tip = host.querySelector('[role="tooltip"]');
    expect(tip?.textContent).toContain('@fatima');
    expect(tip?.textContent).toContain('Responsable de caisse · Agent de dépôt');
    expect(host.getAttribute('aria-describedby')).toBe(tip?.id ?? 'missing');

    host.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(host.querySelector('[role="tooltip"]')).toBeNull();
  });

  it('waits a moment on hover, so sweeping across a list opens nothing', fakeAsync(() => {
    render(fatima);
    host.dispatchEvent(new MouseEvent('mouseenter'));
    tick(100);
    host.dispatchEvent(new MouseEvent('mouseleave'));
    tick(500);
    fixture.detectChanges();
    expect(host.querySelector('[role="tooltip"]')).toBeNull();

    host.dispatchEvent(new MouseEvent('mouseenter'));
    tick(400);
    fixture.detectChanges();
    expect(host.querySelector('[role="tooltip"]')).not.toBeNull();
  }));

  it('has no card, and takes no focus, when told not to', () => {
    render(fatima, { tooltip: false });
    host.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();

    expect(host.querySelector('[role="tooltip"]')).toBeNull();
    expect(host.getAttribute('tabindex')).toBeNull();
  });
});
