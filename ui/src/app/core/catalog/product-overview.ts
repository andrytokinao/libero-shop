import { ProductMovement } from '../models';
import { ProductMenuEntry } from './product-menu';

/** How many receipts the quick sheet lists: the last few are what is looked for. */
export const RECENT_RECEIPTS = 3;

/** The latest receipts of a product, newest first, out of one page of its history. */
export function recentReceipts(movements: readonly ProductMovement[]): ProductMovement[] {
  return movements.filter((movement) => movement.type === 'SUPPLY').slice(0, RECENT_RECEIPTS);
}

/** The menu's entries as rows of buttons, one row per family, in the menu's order. */
export function actionRows(entries: readonly ProductMenuEntry[]): { group: string; entries: ProductMenuEntry[] }[] {
  const rows: { group: string; entries: ProductMenuEntry[] }[] = [];
  for (const entry of entries) {
    const row = rows.find((candidate) => candidate.group === entry.group);
    if (row) {
      row.entries.push(entry);
    } else {
      rows.push({ group: entry.group, entries: [entry] });
    }
  }
  return rows;
}
