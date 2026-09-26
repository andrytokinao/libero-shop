import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ROLE_LABELS, ROLE_PRECEDENCE, RoleApp, orderRoles } from '../../core/models';

/**
 * The jobs an account may do, as a list of boxes.
 *
 * <p>Boxes rather than a drop-down because an account holds a set: in a small grocery the
 * owner sells at the desk, hands the goods over and counts the stock, and a single-choice
 * control would force three accounts for one person. A depot large enough to split the
 * duties simply ticks one box.
 *
 * <p>The full titles are shown, not the short labels, because this is where the choice is
 * made — "Responsable entrée-sortie dépôt" says what the box grants, "Stock" does not.
 */
@Component({
  selector: 'app-role-picker',
  standalone: true,
  template: `
    <div class="roles">
      @for (role of allRoles; track role) {
        <label class="role-box" [class.on]="has(role)">
          <input
            type="checkbox"
            [checked]="has(role)"
            [disabled]="disabled"
            (change)="toggle(role)"
          />
          <span>{{ labels[role] }}</span>
        </label>
      }
    </div>
    @if (!selected.length) {
      <div class="role-warn">
        Un compte doit avoir au moins un rôle : sans rôle, il n'aurait aucune page où aller
        après la connexion.
      </div>
    }
  `,
  styles: `
    .roles {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .role-box {
      display: flex;
      align-items: center;
      gap: 9px;
      padding: 8px 11px;
      border: 1px solid var(--line);
      border-radius: 8px;
      font-size: 12.5px;
      cursor: pointer;
      background: #fff;

      &.on {
        border-color: var(--brand);
        background: var(--brand-soft);
        font-weight: 600;
      }

      input {
        margin: 0;
        cursor: pointer;
      }
    }

    .role-warn {
      margin-top: 8px;
      font-size: 11.5px;
      color: var(--amber);
    }
  `,
})
export class RolePickerComponent {
  /** Bound two-way as `[(selected)]`. Always kept in the server's precedence order. */
  @Input({ required: true }) selected!: RoleApp[];
  @Input() disabled = false;
  @Output() readonly selectedChange = new EventEmitter<RoleApp[]>();

  protected readonly allRoles = ROLE_PRECEDENCE;
  protected readonly labels = ROLE_LABELS;

  protected has(role: RoleApp): boolean {
    return this.selected.includes(role);
  }

  protected toggle(role: RoleApp): void {
    const next = this.has(role)
      ? this.selected.filter((held) => held !== role)
      : [...this.selected, role];
    // Re-ordered on every change so the payload, the badges and the server all agree.
    this.selectedChange.emit(orderRoles(next));
  }
}
