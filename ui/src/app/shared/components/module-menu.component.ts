import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RoleNavigation } from '../../core/config/navigation';
import { AuthService } from '../../core/services/auth.service';

/**
 * A module's screens as large cards, two per row.
 *
 * <p>Where a phone lands after picking "Caisse", "Dépôt"… in the drawer: the drawer only
 * lists the modules, and this page is the one place to find "Nouvelle vente" or "Ventes du
 * jour" with a thumb. Each screen of the module closes back onto it.
 */
@Component({
  selector: 'app-module-menu',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (section(); as s) {
      <nav class="module-cards" [attr.aria-label]="s.label">
        @for (item of s.items; track item.path) {
          <a class="module-card" [routerLink]="item.path">
            <span class="ic" aria-hidden="true">{{ item.icon }}</span>
            <span class="lbl">{{ item.label }}</span>
          </a>
        }
      </nav>
    }
  `,
})
export class ModuleMenuComponent {
  private readonly segment: string = inject(ActivatedRoute).snapshot.data['segment'];
  private readonly auth = inject(AuthService);

  /**
   * The segment comes from the module's parent route, which the role guard already checked.
   * Read from the account's menu, so the cards are the screens the shop's configuration keeps.
   */
  protected readonly section = computed<RoleNavigation | null>(
    () => this.auth.menu().find((s) => s.segment === this.segment) ?? null,
  );
}
