import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CategoryNode, CategoryStock, MAX_CATEGORY_DEPTH } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { CategoryPickerComponent } from '../../shared/components/category-picker.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/**
 * The rayons of the shop, and the tree they make.
 *
 * <p>Shown as an indented list rather than as collapsible branches, because the whole thing is a
 * few dozen rows: collapsing would hide the one relationship the screen exists to show. The
 * indentation comes free from the order the server sends — depth-first, parents before children.
 *
 * <p>Each row carries what it holds, because every decision on this screen turns on that.
 * Renaming a rayon is free; moving one carries its branch; deleting one is only safe when you can
 * see the goods that would be left behind. So the references and the stock value sit on the line
 * with the buttons, and the delete prompt asks where the goods go rather than refusing and leaving
 * the operator to work it out.
 *
 * <p>Two refusals are mirrored here as disabled buttons instead of being waited for: a rayon with
 * sub-rayons cannot be deleted, and a rayon at the last allowed level cannot take a child. The
 * server refuses both anyway; saying so up front beats a toast after the click.
 */
@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [FormsModule, CategoryPickerComponent, AriaryPipe],
  template: `
    <div class="card">
      <div class="head-row">
        <h2>
          Catégories
          <small>
            l'arborescence des rayons — un rayon peut en contenir d'autres, jusqu'à
            {{ maxDepth }} niveaux
          </small>
        </h2>
        <button class="btn small" type="button" (click)="openCreate()">＋ Nouvelle catégorie</button>
      </div>

      @if (tree().length) {
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Rayon</th>
                <th class="num">Sous-rayons</th>
                <th class="num">Références</th>
                <th class="num">Unités</th>
                <th class="num">Valeur du stock</th>
                <th class="col-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (node of tree(); track node.id) {
                <tr>
                  <td>
                    <span class="cat-indent" [style.width.px]="node.depth * 18"></span>
                    @if (node.depth) {
                      <span class="cat-branch muted">└</span>
                    }
                    <span [class.cat-root]="!node.depth">{{ node.name }}</span>
                  </td>
                  <td class="num muted">{{ node.children || '—' }}</td>
                  <td class="num">{{ statOf(node.id).references || '—' }}</td>
                  <td class="num">{{ statOf(node.id).units || '—' }}</td>
                  <td class="num">
                    {{ statOf(node.id).value ? (statOf(node.id).value | ariary) : '—' }}
                  </td>
                  <td class="col-actions">
                    <div class="actions">
                      <button
                        class="btn small ghost"
                        type="button"
                        [disabled]="busyId() === node.id"
                        (click)="openEdit(node)"
                      >
                        Modifier
                      </button>
                      <button
                        class="btn small ghost danger"
                        type="button"
                        [disabled]="busyId() === node.id || node.children > 0"
                        [title]="
                          node.children
                            ? 'Videz ou déplacez ses ' + node.children + ' sous-rayon(s) d’abord'
                            : 'Supprime le rayon ; ses produits sont déplacés ailleurs'
                        "
                        (click)="openDelete(node)"
                      >
                        Supprimer
                      </button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="empty">
          Aucune catégorie. Créez-en une, ou laissez un import de produits les créer à partir de la
          colonne « catégorie » du fichier.
        </div>
      }
    </div>

    @if (editing(); as target) {
      <div class="modal-backdrop" (click)="onBackdrop($event, closeForm)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="cat-title">
          <div class="modal-head">
            <div>
              <h2 id="cat-title">
                {{ target.id ? 'Modifier la catégorie' : 'Nouvelle catégorie' }}
              </h2>
              @if (target.id) {
                <div class="sub muted">
                  Renommer n'affecte aucun produit. La déplacer emmène ses sous-rayons avec elle.
                </div>
              }
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="closeForm()">✕</button>
          </div>
          <div class="modal-body">
            <div class="fld">
              <label for="cat-name">Nom</label>
              <input
                id="cat-name"
                type="text"
                autocomplete="off"
                maxlength="60"
                placeholder="Boissons fraîches"
                [ngModel]="formName()"
                (ngModelChange)="formName.set($event)"
              />
              <div class="hint-line">
                Unique dans tout l'arbre : un fichier d'import qui n'écrit qu'un nom doit
                toujours désigner le même rayon.
              </div>
            </div>
            <div class="fld">
              <label for="cat-parent">À l'intérieur de</label>
              <app-category-picker
                fieldId="cat-parent"
                [categories]="tree()"
                [allowNone]="true"
                noneLabel="Aucune — catégorie principale"
                [blocked]="blockedParents()"
                [value]="formParent()"
                (valueChange)="formParent.set($event)"
              />
            </div>
            @if (formProblem(); as message) {
              <div class="dialog-problem">{{ message }}</div>
            }
          </div>
          <div class="modal-foot">
            <button class="btn ghost" type="button" (click)="closeForm()">Annuler</button>
            <button
              class="btn"
              type="button"
              [disabled]="saving() || !!formProblem()"
              (click)="save()"
            >
              {{ saving() ? 'Enregistrement...' : target.id ? 'Enregistrer' : 'Créer' }}
            </button>
          </div>
        </div>
      </div>
    }

    @if (deleting(); as target) {
      <div class="modal-backdrop" (click)="onBackdrop($event, cancelDelete)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="cat-del-title">
          <div class="modal-head">
            <div>
              <h2 id="cat-del-title">Supprimer « {{ target.name }} » ?</h2>
              <div class="sub muted">{{ target.path }}</div>
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="cancelDelete()">✕</button>
          </div>
          <div class="modal-body">
            @if (statOf(target.id).references) {
              <p style="margin:0 0 12px;">
                Ce rayon contient <strong>{{ statOf(target.id).references }}</strong> référence(s).
                Elles ne peuvent pas rester sans rayon : choisissez où elles vont.
              </p>
              <div class="fld">
                <label for="cat-move">Déplacer les produits vers</label>
                <app-category-picker
                  fieldId="cat-move"
                  [categories]="tree()"
                  [allowNone]="true"
                  noneLabel="— à choisir"
                  [blocked]="deleteBlocked()"
                  [value]="moveTo()"
                  (valueChange)="moveTo.set($event)"
                />
              </div>
            } @else {
              <p style="margin:0;">
                Ce rayon est vide. Sa suppression ne touche à aucun produit.
              </p>
            }
          </div>
          <div class="modal-foot">
            <button class="btn ghost" type="button" (click)="cancelDelete()">Annuler</button>
            <button
              class="btn danger-solid"
              type="button"
              [disabled]="saving() || (!!statOf(target.id).references && moveTo() === null)"
              (click)="confirmDelete()"
            >
              {{ saving() ? 'Suppression...' : 'Supprimer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    /* The indentation is a spacer rather than padding on the cell, so the branch glyph stays
       glued to the name whatever the depth. */
    .cat-indent {
      display: inline-block;
    }

    .cat-branch {
      margin-right: 6px;
    }

    .cat-root {
      font-weight: 600;
    }

    .col-actions {
      width: 1%;
      white-space: nowrap;
    }

    .actions {
      display: flex;
      gap: 6px;
    }

    .fld {
      display: flex;
      flex-direction: column;
      gap: 5px;
      margin-bottom: 14px;

      label {
        font-size: 11.5px;
        color: var(--ink-soft);
        font-weight: 600;
      }

      input {
        width: 100%;
      }
    }

    .hint-line {
      font-size: 11.5px;
      color: var(--ink-soft);
    }

    .dialog-problem {
      background: var(--amber-soft);
      color: var(--amber);
      border-radius: 7px;
      padding: 9px 11px;
      font-size: 12.5px;
    }

    .btn.danger {
      color: var(--red);
      border-color: var(--red-soft);

      &:hover:not(:disabled) {
        background: var(--red-soft);
      }
    }

    .btn.danger-solid {
      background: var(--red);
      border-color: var(--red);
    }
  `,
})
export class CategoriesComponent {
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);

  protected readonly maxDepth = MAX_CATEGORY_DEPTH;

  private readonly treeResource = apiResource<CategoryNode[]>([], () => this.api.categories());
  // What each rayon holds. From the same endpoint the stock screens use, so the figures agree.
  private readonly statResource = apiResource<CategoryStock[]>([], () =>
    this.api.stockByCategory(),
  );

  protected readonly tree = this.treeResource.value;

  protected readonly busyId = signal<number | null>(null);
  protected readonly saving = signal(false);

  /** The rayon being created or corrected; `id: null` means a new one. */
  protected readonly editing = signal<{ id: number | null; name: string; path: string } | null>(
    null,
  );
  protected readonly formName = signal('');
  protected readonly formParent = signal<number | null>(null);

  protected readonly deleting = signal<CategoryNode | null>(null);
  protected readonly moveTo = signal<number | null>(null);

  private readonly stats = computed(() => {
    const byId = new Map<number, CategoryStock>();
    this.statResource.value().forEach((row) => byId.set(row.category.id, row));
    return byId;
  });

  protected statOf(categoryId: number): { references: number; units: number; value: number } {
    const row = this.stats().get(categoryId);
    return { references: row?.references ?? 0, units: row?.units ?? 0, value: row?.value ?? 0 };
  }

  /**
   * Rayons the parent picker must not offer: the one being edited, everything under it, and
   * anything already at the last allowed level.
   *
   * <p>The descendants fall out of the flat ordering for free. The server sends the tree
   * depth-first, so the rows following a node with a greater depth than its own *are* its
   * subtree, up to the first row at the same depth or above. No id chasing needed.
   */
  protected readonly blockedParents = computed<number[]>(() => {
    const tree = this.tree();
    const blocked = tree
      .filter((node) => node.depth >= MAX_CATEGORY_DEPTH - 1)
      .map((node) => node.id);

    const self = this.editing()?.id;
    if (self == null) {
      return blocked;
    }
    const start = tree.findIndex((node) => node.id === self);
    if (start < 0) {
      return blocked;
    }
    blocked.push(self);
    for (let i = start + 1; i < tree.length && tree[i].depth > tree[start].depth; i++) {
      blocked.push(tree[i].id);
    }
    return blocked;
  });

  /**
   * The rayon being deleted cannot receive its own goods. A computed rather than an inline
   * array literal, so the picker is not handed a new array on every change-detection pass.
   */
  protected readonly deleteBlocked = computed<number[]>(() => {
    const target = this.deleting();
    return target ? [target.id] : [];
  });

  protected readonly formProblem = computed<string | null>(() => {
    if (!this.formName().trim()) {
      return 'Le nom est obligatoire.';
    }
    if (this.blockedParents().includes(this.formParent() ?? -1)) {
      return "Cette catégorie ne peut pas accueillir celle-ci : elle est déjà en dessous d'elle, ou déjà au dernier niveau.";
    }
    return null;
  });

  protected openCreate(): void {
    this.editing.set({ id: null, name: '', path: '' });
    this.formName.set('');
    this.formParent.set(null);
  }

  protected openEdit(node: CategoryNode): void {
    this.editing.set({ id: node.id, name: node.name, path: node.path });
    this.formName.set(node.name);
    this.formParent.set(node.parentId);
  }

  protected readonly closeForm = (): void => {
    this.editing.set(null);
  };

  protected save(): void {
    const target = this.editing();
    if (!target || this.formProblem()) {
      return;
    }
    const payload = { name: this.formName().trim(), parentId: this.formParent() };
    this.saving.set(true);

    const call = target.id
      ? this.api.updateCategory(target.id, payload)
      : this.api.createCategory(payload);

    call.subscribe({
      next: (node) => {
        this.saving.set(false);
        this.editing.set(null);
        this.reload();
        this.toasts.show(
          target.id ? `Catégorie « ${node.path} » enregistrée.` : `Catégorie « ${node.path} » créée.`,
        );
      },
      error: () => this.saving.set(false),
    });
  }

  protected openDelete(node: CategoryNode): void {
    this.deleting.set(node);
    this.moveTo.set(null);
  }

  protected readonly cancelDelete = (): void => {
    this.deleting.set(null);
  };

  protected confirmDelete(): void {
    const target = this.deleting();
    if (!target) {
      return;
    }
    this.saving.set(true);
    this.busyId.set(target.id);
    this.api.deleteCategory(target.id, this.moveTo()).subscribe({
      next: (removed) => {
        this.saving.set(false);
        this.busyId.set(null);
        this.deleting.set(null);
        this.reload();
        this.toasts.show(
          removed.productsMoved
            ? `« ${target.name} » supprimée, ${removed.productsMoved} produit(s) déplacé(s).`
            : `« ${target.name} » supprimée.`,
        );
      },
      error: () => {
        this.saving.set(false);
        this.busyId.set(null);
      },
    });
  }

  /** Both resources: a move changes the tree, and a delete changes what each rayon holds. */
  private reload(): void {
    this.treeResource.reload();
    this.statResource.reload();
  }

  protected onBackdrop(event: MouseEvent, close: () => void): void {
    if (event.target === event.currentTarget) {
      close();
    }
  }
}
