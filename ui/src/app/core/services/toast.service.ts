import { Injectable, signal } from '@angular/core';

const TOAST_DURATION_MS = 3200;
/** Long enough to read the message and reach a button. */
const TOAST_WITH_ACTIONS_MS = 7000;

/** A button in the message: what to do next, right where the last thing was done. */
export interface ToastAction {
  readonly label: string;
  readonly run: () => void;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string | null>(null);
  readonly actions = signal<readonly ToastAction[]>([]);
  private timer?: ReturnType<typeof setTimeout>;

  show(message: string, actions: readonly ToastAction[] = []): void {
    clearTimeout(this.timer);
    this.message.set(message);
    this.actions.set(actions);
    this.timer = setTimeout(() => this.dismiss(), actions.length ? TOAST_WITH_ACTIONS_MS : TOAST_DURATION_MS);
  }

  /** A button of the message: the message goes, the action runs. */
  act(action: ToastAction): void {
    this.dismiss();
    action.run();
  }

  dismiss(): void {
    clearTimeout(this.timer);
    this.message.set(null);
    this.actions.set([]);
  }
}
