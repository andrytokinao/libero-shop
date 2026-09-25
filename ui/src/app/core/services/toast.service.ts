import { Injectable, signal } from '@angular/core';

const TOAST_DURATION_MS = 3200;

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string | null>(null);
  private timer?: ReturnType<typeof setTimeout>;

  show(message: string): void {
    clearTimeout(this.timer);
    this.message.set(message);
    this.timer = setTimeout(() => this.message.set(null), TOAST_DURATION_MS);
  }

  dismiss(): void {
    clearTimeout(this.timer);
    this.message.set(null);
  }
}
