import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ROLE_LABELS, RoleApp, UserApp } from '../models';
import { ROLE_NAVIGATION, homePathOf } from '../config/navigation';
import { ShopStore } from './shop-store.service';

/**
 * Holds who is logged in. The demo has no authentication: the sidebar lets you
 * impersonate any seeded user so all four role views can be walked through.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly store = inject(ShopStore);
  private readonly router = inject(Router);

  readonly availableUsers = this.store.users;
  private readonly userId = signal<number>(this.store.users()[0].id);

  readonly currentUser = computed<UserApp>(
    () => this.availableUsers().find((u) => u.id === this.userId()) ?? this.availableUsers()[0],
  );

  readonly role = computed(() => this.currentUser().role);
  readonly roleLabel = computed(() => ROLE_LABELS[this.role()]);
  readonly menu = computed(() => ROLE_NAVIGATION[this.role()].items);
  readonly initials = computed(() =>
    this.currentUser()
      .fullName.split(' ')
      .map((part) => part[0])
      .slice(0, 2)
      .join(''),
  );

  switchUser(userId: number): void {
    const target = this.availableUsers().find((u) => u.id === userId);
    if (!target) {
      return;
    }
    this.userId.set(target.id);
    void this.router.navigateByUrl(homePathOf(target.role));
  }

  homePath(): string {
    return homePathOf(this.role());
  }

  owns(segment: string): boolean {
    return ROLE_NAVIGATION[this.role()].segment === segment;
  }

  /** Every user holding the given role, used by the depot/admin screens. */
  usersWithRole(role: RoleApp): UserApp[] {
    return this.availableUsers().filter((u) => u.role === role);
  }
}
