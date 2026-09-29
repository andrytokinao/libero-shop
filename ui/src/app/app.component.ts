import { DatePipe } from '@angular/common';
import { Component, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/services/auth.service';
import { PageTitleStrategy } from './core/services/page-title.strategy';
import { LicenseBannerComponent } from './shared/components/license-banner.component';
import { NotificationBellComponent } from './shared/components/notification-bell.component';
import { ToastComponent } from './shared/components/toast.component';

/** Application shell: role sidebar, licence bar, top bar, routed page and toast host. */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    DatePipe,
    LicenseBannerComponent,
    NotificationBellComponent,
    ToastComponent,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly pageTitle = inject(PageTitleStrategy).pageTitle;
  protected readonly today = new Date();

  /**
   * Drawer state of the sidebar. Only meaningful on a phone, where the menu sits
   * off-canvas; above the breakpoint the sidebar is always visible and this is ignored.
   */
  protected readonly navOpen = signal(false);

  constructor() {
    // A tap on a menu entry should leave the page visible, not the menu that led to it.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.navOpen.set(false));
  }

  protected toggleNav(): void {
    this.navOpen.update((open) => !open);
  }

  @HostListener('document:keydown.escape')
  protected closeNav(): void {
    this.navOpen.set(false);
  }

  protected logout(): void {
    this.navOpen.set(false);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}
