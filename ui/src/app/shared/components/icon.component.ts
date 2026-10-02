import { Component, computed, input } from '@angular/core';
import { ICONS, IconName } from '../../core/ui/icons';

/**
 * One of the application's {@link ICONS}, drawn inline in the current text colour.
 *
 * <p>Decorative by default — hidden from screen readers, the text beside it says the thing. Give
 * it a `label` when it stands alone (an icon-only button), and it is announced instead.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  host: { class: 'app-icon' },
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="size()"
      [attr.height]="size()"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      focusable="false"
      [attr.aria-hidden]="label() ? null : 'true'"
      [attr.role]="label() ? 'img' : null"
      [attr.aria-label]="label()"
    >
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: 0 0 auto;
      line-height: 0;
    }
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  /** In pixels, both ways. */
  readonly size = input(18);
  /** What the icon means, for a screen reader, when no text beside it says so. */
  readonly label = input<string | null>(null);

  protected readonly paths = computed(() => ICONS[this.name()]);
}
