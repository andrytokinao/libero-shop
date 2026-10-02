import { DatePipe } from '@angular/common';
import {
  Component,
  HostListener,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { LiveUpdateService } from './core/config/live-update/live-update.service';
import { moduleMenuPath, sectionOfUrl } from './core/config/navigation';
import { ServerConfig } from './core/config/server-config.service';
import { RoleApp } from './core/models';
import { OrderEvents } from './core/realtime/order-events.service';
import { StockEvents } from './core/realtime/stock-events.service';
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
  protected readonly server = inject(ServerConfig);
  protected readonly liveUpdate = inject(LiveUpdateService);
  private readonly router = inject(Router);
  protected readonly pageTitle = inject(PageTitleStrategy).pageTitle;
  protected readonly today = new Date();

  /**
   * Drawer state of the sidebar. Only meaningful on a phone, where the menu sits
   * off-canvas; above the breakpoint the sidebar is always visible and this is ignored.
   */
  protected readonly navOpen = signal(false);

  private readonly url = signal(this.router.url);

  /** The module of the current page — the one highlighted in the phone drawer. */
  protected readonly currentSection = computed(() => sectionOfUrl(this.url()));

  /**
   * Where the phone's "Fermer" button leads: back to the module's cards, not to the home
   * page. Null on the cards themselves, which have nothing to close.
   */
  protected readonly closeTarget = computed(() => {
    const section = this.currentSection();
    if (section === null) {
      return null;
    }
    const menu = this.moduleMenuPath(section);
    return this.url().split(/[?#]/)[0] === menu ? null : menu;
  });

  protected readonly moduleMenuPath = moduleMenuPath;

  /**
   * The phone's always-there button to serve the next customer: taking an order for whoever
   * takes orders, a sale at the till otherwise. Hidden on that screen itself, and for the
   * accounts that neither sell nor take orders.
   */
  protected readonly quickAction = computed(() => {
    const roles = this.auth.roles();
    const action = roles.includes(RoleApp.ORDER_TAKER)
      ? { path: '/commandes/nouvelle', label: 'Nouvelle commande' }
      : roles.includes(RoleApp.CASHIER)
        ? { path: '/caisse/nouvelle-vente', label: 'Nouvelle vente' }
        : null;
    return action && !this.url().startsWith(action.path) ? action : null;
  });

  constructor() {
    // The order and stock events are heard from the first screen on, whichever it is: the stores
    // the screens read stay current behind them.
    inject(OrderEvents);
    inject(StockEvents);

    // On a phone: pages kept up to date from the server, checked again whenever the app comes
    // back to the foreground or is pointed at another server.
    this.liveUpdate.start();
    effect(() => {
      if (this.server.serverUrl() !== null) {
        untracked(() => void this.liveUpdate.check());
      }
    });

    // A tap on a menu entry should leave the page visible, not the menu that led to it.
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        this.navOpen.set(false);
        this.url.set(event.urlAfterRedirects);
      });
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
