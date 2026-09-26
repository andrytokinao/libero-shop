import { Component, EventEmitter, Input, Output, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CategoryNode } from '../../core/models';

/** One indent level, as non-breaking spaces — see the note on `label` below. */
const INDENT = '   ';

/**
 * Picking a rayon out of the tree.
 *
 * <p>A `<select>` rather than a collapsible tree, and the reason is the till. These screens are
 * used on a phone behind a counter as much as on a laptop, where a native select opens the
 * platform's own wheel — reachable with a thumb, searchable by typing, and already understood.
 * A hand-rolled tree would look better on the laptop and be worse everywhere else.
 *
 * <p>The hierarchy survives the flattening because the server sends the rows in depth-first
 * order: a parent is immediately followed by its children, so prefixing each label with its
 * depth is enough to draw the tree. Non-breaking spaces do that prefixing rather than CSS,
 * because `padding` on an `<option>` is honoured by roughly no browser.
 *
 * <p>Nothing here filters the choice down to leaves. A shop files goods on a parent rayon as
 * readily as on a child — "Boissons" holds the odd bottle that belongs to no sub-rayon — and
 * forbidding that would be this component inventing a rule the catalogue does not have.
 */
@Component({
  selector: 'app-category-picker',
  standalone: true,
  imports: [FormsModule],
  template: `
    <select
      class="field"
      [id]="fieldId()"
      [disabled]="disabled()"
      [ngModel]="value"
      (ngModelChange)="valueChange.emit($event)"
    >
      @if (allowNone()) {
        <option [ngValue]="null">{{ noneLabel() }}</option>
      }
      @for (node of categories(); track node.id) {
        <option [ngValue]="node.id" [disabled]="blocked().includes(node.id)">
          {{ label(node) }}
        </option>
      }
    </select>
  `,
})
export class CategoryPickerComponent {
  /** The tree as the server sent it: depth-first, parents before children. */
  readonly categories = input.required<readonly CategoryNode[]>();
  readonly allowNone = input(true);
  readonly noneLabel = input('Toutes les catégories');
  readonly disabled = input(false);
  /**
   * Rayons that may be listed but not chosen — a rayon and its own descendants, when the
   * picker is choosing a *parent* for it. Shown greyed rather than hidden: a disappearing
   * option reads as a bug, a greyed one reads as "not that one".
   */
  readonly blocked = input<readonly number[]>([]);
  /** Set when the picker sits under a `<label for>`. */
  readonly fieldId = input<string | undefined>(undefined);

  /**
   * Bound the old way rather than as a signal input: `[(value)]` on a signal input needs the
   * two-way `model()`, and the parents here drive it from their own signals anyway.
   */
  @Input() value: number | null = null;
  @Output() readonly valueChange = new EventEmitter<number | null>();

  /** `Eau` under `Boissons` reads as `└ Eau`, one indent in. */
  protected label(node: CategoryNode): string {
    return node.depth === 0 ? node.name : `${INDENT.repeat(node.depth - 1)}└ ${node.name}`;
  }
}
