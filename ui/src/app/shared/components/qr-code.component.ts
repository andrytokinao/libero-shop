import { Component, Input, signal } from '@angular/core';
import { toDataURL } from 'qrcode';

/**
 * A QR code drawn in the browser, from any text.
 *
 * <p>Generated here rather than by the server: it is a picture of a string the page already
 * holds, and a round trip per code would only make the screen slower to show them.
 */
@Component({
  selector: 'app-qr-code',
  standalone: true,
  template: `
    @if (image(); as src) {
      <img [src]="src" [attr.alt]="alt" [width]="size" [height]="size" />
    }
  `,
  styles: `
    img {
      display: block;
      image-rendering: pixelated;
    }
  `,
})
export class QrCodeComponent {
  @Input({ required: true }) set value(text: string) {
    // Medium correction: survives a screen's glare and a slightly shaky phone. Drawn at a fixed
    // resolution and scaled by the <img>, so it does not depend on which input arrives first.
    toDataURL(text, { errorCorrectionLevel: 'M', margin: 1, width: 480 })
      .then((url) => this.image.set(url))
      .catch(() => this.image.set(null));
  }

  @Input() size = 200;
  @Input() alt = 'QR code';

  protected readonly image = signal<string | null>(null);
}
