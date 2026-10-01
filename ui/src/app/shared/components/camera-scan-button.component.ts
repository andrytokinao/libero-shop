import { Component, inject, output, signal } from '@angular/core';
import { CodeScanner, PRODUCT_BARCODE_FORMATS } from '../../core/config/code-scanner.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Reads a product barcode with the phone's camera and hands it over.
 *
 * <p>Renders nothing outside the installed app, where there is no camera scanner to call: the
 * screens that hold it need no condition of their own. What to do with the code is the
 * screen's business; this only reads it, and says why when it could not.
 */
@Component({
  selector: 'app-camera-scan-button',
  standalone: true,
  template: `
    @if (scanner.available) {
      <button
        type="button"
        class="btn ghost"
        title="Scanner un code-barres avec la caméra"
        aria-label="Scanner un code-barres avec la caméra"
        [disabled]="busy()"
        (click)="scan()"
      >
        📷
      </button>
    }
  `,
})
export class CameraScanButtonComponent {
  protected readonly scanner = inject(CodeScanner);
  private readonly toasts = inject(ToastService);

  /** The code read; nothing is emitted when the user backs out of the scanner. */
  readonly scanned = output<string>();

  protected readonly busy = signal(false);

  protected async scan(): Promise<void> {
    this.busy.set(true);
    try {
      const code = await this.scanner.scan(PRODUCT_BARCODE_FORMATS);
      if (code) {
        this.scanned.emit(code);
      }
    } catch (error) {
      this.toasts.show((error as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
