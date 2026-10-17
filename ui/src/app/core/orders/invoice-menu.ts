import { AuthService } from '../services/auth.service';
import { InvoiceAction, InvoiceActionContext, InvoiceActionKind } from './invoice-actions';

/** The menu entry that opens the order's detail, beside the actions of its detail. */
export const SHOW_DETAIL = 'DETAIL';

/** What an invoice's "⋮" can ask for: its detail, or one of the detail's actions. */
export type InvoiceMenuId = typeof SHOW_DETAIL | InvoiceActionKind;

/** One entry of the "⋮", as the shared action menu draws it. */
export interface InvoiceMenuEntry {
  readonly id: InvoiceMenuId;
  readonly label: string;
  readonly danger?: boolean;
}

/** Who is looking and how the shop is set up, as the rules of `availableActions` read it. */
export function invoiceActionContext(auth: AuthService): InvoiceActionContext {
  return {
    userId: auth.currentUser()?.id ?? null,
    roles: auth.roles(),
    features: auth.settings(),
    handOverLabel: auth.words().handOverAction,
  };
}

/**
 * The "⋮" of an invoice line: its articles first, then the very actions its detail offers —
 * read from the same rules, so the menu and the detail never disagree.
 */
export function invoiceMenu(actions: readonly InvoiceAction[]): InvoiceMenuEntry[] {
  return [
    { id: SHOW_DETAIL, label: 'Voir les articles' },
    ...actions.map((action) => ({
      id: action.kind,
      label: action.label,
      ...(action.tone === 'danger' ? { danger: true } : {}),
    })),
  ];
}
