/**
 * One key of a screen's keyboard map: what it is called on the legend and what it does.
 * A command object, so the legend shown to the cashier and the keys actually handled are
 * read from the same list and cannot drift apart.
 */
export interface Shortcut {
  /** `KeyboardEvent.key`, e.g. `F10`. */
  readonly key: string;
  /** What the legend says, in the cashier's words. */
  readonly label: string;
  readonly run: () => void;
  /** Greyed on the legend and ignored when false; always available when absent. */
  readonly enabled?: () => boolean;
}

/**
 * Dispatches key presses to a screen's shortcuts.
 *
 * <p>Function keys only, on purpose: they are never part of what a cashier types, so a
 * shortcut can never swallow a letter of a name or a digit of a code — and a barcode scanner,
 * which types like a keyboard, can never trigger one. Combinations with Ctrl, Alt or Meta are
 * left to the browser and the system.
 */
export class ShortcutMap {
  constructor(readonly shortcuts: readonly Shortcut[]) {}

  /**
   * @returns true when the key was one of this map's, and the browser's own use of it is
   *   cancelled. Never bind this straight to a template event: Angular cancels the event when a
   *   handler returns false, which would block every other key. Call it from a void method.
   */
  handle(event: KeyboardEvent): boolean {
    if (event.ctrlKey || event.altKey || event.metaKey) {
      return false;
    }
    const shortcut = this.shortcuts.find((candidate) => candidate.key === event.key);
    if (!shortcut) {
      return false;
    }
    // Cancelled even when disabled: F10 must never fall through to the browser's menu bar
    // because the cart happened to be empty.
    event.preventDefault();
    if (isEnabled(shortcut)) {
      shortcut.run();
    }
    return true;
  }
}

export function isEnabled(shortcut: Shortcut): boolean {
  return shortcut.enabled?.() ?? true;
}
