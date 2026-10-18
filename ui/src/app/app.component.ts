import {
  Component,
  DestroyRef,
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
import { moduleMenuPath, openSections, sectionOfUrl } from './core/config/navigation';
import { ServerConfig } from './core/config/server-config.service';
import { RoleApp } from './core/models';
import { OrderEvents } from './core/realtime/order-events.service';
import { StockEvents } from './core/realtime/stock-events.service';
import { AuthService } from './core/services/auth.service';
import { SessionKeeper } from './core/services/session-keeper.service';
import { ServerApi } from './core/api/server.api';
import { setShopClock } from './core/utils/date.util';
import { PageTitleStrategy } from './core/services/page-title.strategy';
import { ThemeService } from './core/services/theme.service';
import { LicenseBannerComponent } from './shared/components/license-banner.component';
import { MaintenanceWatcherComponent } from './shared/components/maintenance-watcher.component';
import { NotificationBellComponent } from './shared/components/notification-bell.component';
import { IconComponent } from './shared/components/icon.component';
import { ProfileDialogComponent } from './shared/components/profile-dialog.component';
import { ToastComponent } from './shared/components/toast.component';
import { UserAvatarComponent } from './shared/components/user-avatar.component';

/** Application shell: role sidebar, licence bar, top bar, routed page and toast host. */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    LicenseBannerComponent,
    NotificationBellComponent,
    ToastComponent,
    UserAvatarComponent,
    ProfileDialogComponent,
    IconComponent,
    MaintenanceWatcherComponent,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
  protected readonly session = inject(SessionKeeper);
  protected readonly server = inject(ServerConfig);
  protected readonly liveUpdate = inject(LiveUpdateService);
  private readonly router = inject(Router);
  protected readonly pageTitle = inject(PageTitleStrategy).pageTitle;

  /**
   * Drawer state of the sidebar. Only meaningful on a phone, where the menu sits
   * off-canvas; above the breakpoint the sidebar is always visible and this is ignored.
   */
  protected readonly navOpen = signal(false);
  /** "Mon profil", opened from the person's name in the sidebar or their face in the phone bar. */
  protected readonly profileOpen = signal(false);

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

  /** The role sections unfolded by hand in the sidebar, besides the one of the page on screen. */
  private readonly unfolded = signal<ReadonlySet<string>>(new Set());
  protected readonly openSegments = computed(() =>
    openSections(this.auth.menu(), this.currentSection()?.segment ?? null, this.unfolded()),
  );

  protected toggleSection(segment: string): void {
    const unfolded = new Set(this.unfolded());
    if (this.openSegments().has(segment)) {
      unfolded.delete(segment);
    } else {
      unfolded.add(segment);
    }
    this.unfolded.set(unfolded);
  }

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
    // The theme follows the device from the start, the login page included.
    inject(ThemeService);

    // On a phone: pages kept up to date from the server, checked again whenever the app comes
    // back to the foreground or is pointed at another server.
    this.liveUpdate.start();
    // A session in use is prolonged before it ends; an idle one warns first.
    this.session.start(inject(DestroyRef));

    // "Today" is the shop's: once signed in, the dates every screen asks for follow the
    // server's clock, not a phone set to the wrong day or zone.
    const serverApi = inject(ServerApi);
    effect(() => {
      if (this.auth.isAuthenticated()) {
        untracked(() =>
          serverApi.time().subscribe({ next: (time) => setShopClock(time.now), error: () => undefined }),
        );
      }
    });
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
    this.profileOpen.set(false);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}
