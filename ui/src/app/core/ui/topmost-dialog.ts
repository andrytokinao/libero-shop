/**
 * Whether the dialog drawn by this component is the one on top — the last backdrop on the page.
 *
 * <p>Every dialog listens to Escape on the whole document. When one opens over another (an invoice
 * over a product's history, a confirmation over a list), Escape would close both; only the one on
 * top should go. A dialog's backdrop is the first element of its component, which is how it is
 * recognised here.
 */
export function isTopmostDialog(host: HTMLElement): boolean {
  const backdrops = document.querySelectorAll('.modal-backdrop');
  const last = backdrops.item(backdrops.length - 1);
  return !last || last.parentElement === host;
}
