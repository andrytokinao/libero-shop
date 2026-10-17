import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { UserApi } from '../../core/api/user.api';
import { RoleApp, UserActivity, UserApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { UsersComponent } from './users.component';

describe('UsersComponent menu', () => {
  const me = { id: 1, fullName: 'Mparany Solofo', username: 'mparany', roles: [RoleApp.SUPER_ADMIN], enabled: true } as UserApp;
  const fatima = { id: 2, fullName: 'Fatima Randria', username: 'fatima', roles: [RoleApp.CASHIER], enabled: true } as UserApp;
  const lova = { id: 3, fullName: 'Lova Rabe', username: 'lova', roles: [RoleApp.ORDER_TAKER], enabled: false } as UserApp;
  const row = (user: UserApp) => ({ user, salesToday: 0, collectedToday: 0, deliveriesToday: 0, remittances: 0 }) as unknown as UserActivity;

  let fixture: ComponentFixture<UsersComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserApi, useValue: { activity: () => of([row(me), row(fatima), row(lova)]) } },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal(me),
            hasRole: () => true,
            isAuthenticated: () => true,
            applyCurrentUser: () => undefined,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(UsersComponent);
    fixture.detectChanges();
  });

  function menuOf(rowIndex: number): string[] {
    (fixture.nativeElement.querySelectorAll('.am-trigger')[rowIndex] as HTMLButtonElement).click();
    fixture.detectChanges();
    const entries = Array.from(fixture.nativeElement.querySelectorAll('[role=menuitem]') as NodeListOf<HTMLElement>, (e) =>
      e.querySelector('span')?.textContent?.trim() ?? '',
    );
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    return entries;
  }

  it('offers to disable another account, in red, never one\'s own', () => {
    expect(menuOf(0)).toEqual(['Modifier', 'Rôles', 'Changer le mot de passe']);
    expect(menuOf(1)).toEqual(['Modifier', 'Rôles', 'Changer le mot de passe', 'Désactiver']);
  });

  it('offers to enable a disabled account again', () => {
    expect(menuOf(2)).toContain('Réactiver');
  });
});
