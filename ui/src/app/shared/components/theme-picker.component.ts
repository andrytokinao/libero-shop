import { Component, inject } from '@angular/core';
import { ThemeChoice, ThemeService } from '../../core/services/theme.service';
import { IconName } from '../../core/ui/icons';
import { IconComponent } from './icon.component';

interface ThemeOption {
  readonly value: ThemeChoice;
  readonly label: string;
  readonly icon: IconName;
}

const OPTIONS: readonly ThemeOption[] = [
  { value: 'light', label: 'Clair', icon: 'sun' },
  { value: 'dark', label: 'Sombre', icon: 'moon' },
  { value: 'system', label: 'Automatique', icon: 'monitor' },
];

/**
 * Clair, sombre, or as the device is set — three buttons, the current one pressed. The choice
 * applies at once and is kept on this device only.
 */
@Component({
  selector: 'app-theme-picker',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="picker" role="radiogroup" aria-label="Thème">
      @for (option of options; track option.value) {
        <button
          type="button"
          role="radio"
          [class.on]="theme.choice() === option.value"
          [attr.aria-checked]="theme.choice() === option.value"
          (click)="theme.choose(option.value)"
        >
          <app-icon [name]="option.icon" [size]="16" />
          {{ option.label }}
        </button>
      }
    </div>
  `,
  styles: `
    .picker {
      display: inline-flex;
      padding: 3px;
      gap: 3px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--bg);
    }

    button {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 12px;
      border: none;
      border-radius: 7px;
      background: none;
      color: var(--ink-soft);
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;

      &.on {
        background: var(--panel);
        color: var(--ink);
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
      }
    }
  `,
})
export class ThemePickerComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly options = OPTIONS;
}
