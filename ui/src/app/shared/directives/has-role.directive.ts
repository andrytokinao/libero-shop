import { Directive, Input, TemplateRef, ViewContainerRef, effect, inject, signal } from '@angular/core';
import { RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';

/**
 * Renders its content only for the listed roles.
 *
 * <pre>
 *   &lt;button *appHasRole="'CASHIER'"&gt;Imprimer&lt;/button&gt;
 *   &lt;div *appHasRole="['CASHIER', 'SUPER_ADMIN']"&gt;...&lt;/div&gt;
 * </pre>
 *
 * <p>The same {@link AuthService} that backs the routing guards backs the display, so a
 * role's abilities are declared once. This hides controls the API would refuse anyway —
 * it keeps the screen honest, it does not keep anything safe.
 */
@Directive({ selector: '[appHasRole]', standalone: true })
export class HasRoleDirective {
  private readonly auth = inject(AuthService);
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);

  private readonly roles = signal<RoleApp[]>([]);
  private rendered = false;

  @Input({ required: true }) set appHasRole(value: RoleApp | RoleApp[]) {
    this.roles.set(Array.isArray(value) ? value : [value]);
  }

  constructor() {
    // Re-evaluated when the session changes, so signing in or out updates the view
    // without the component having to know about it.
    effect(() => {
      const allowed = this.auth.hasRole(...this.roles());
      if (allowed && !this.rendered) {
        this.viewContainer.createEmbeddedView(this.templateRef);
        this.rendered = true;
      } else if (!allowed && this.rendered) {
        this.viewContainer.clear();
        this.rendered = false;
      }
    });
  }
}
