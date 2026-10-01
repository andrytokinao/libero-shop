import { Injectable } from '@angular/core';
import { BarcodeFormat, BarcodeScanner } from '@capacitor-mlkit/barcode-scanning';
import { Capacitor } from '@capacitor/core';

/**
 * What a product carries in a shop: the retail barcodes (EAN, UPC), the industrial ones a
 * wholesaler prints on cartons, and a QR code for the shop's own labels. Listing them rather
 * than reading everything keeps ML Kit fast and stops it from picking up some other code in view.
 */
export const PRODUCT_BARCODE_FORMATS: BarcodeFormat[] = [
  BarcodeFormat.Ean13,
  BarcodeFormat.Ean8,
  BarcodeFormat.UpcA,
  BarcodeFormat.UpcE,
  BarcodeFormat.Code128,
  BarcodeFormat.Code39,
  BarcodeFormat.QrCode,
];

/**
 * The phone's camera as a code reader — the only code that knows which plugin does it.
 *
 * <p>Uses ML Kit's ready-made scanning screen: on Android it is Google's code scanner, run by
 * Play Services, which needs no camera permission from this app and draws its own interface
 * over the web view. Reads the server's QR code at first launch, and product barcodes with
 * {@link PRODUCT_BARCODE_FORMATS} on the screens that sell.
 */
@Injectable({ providedIn: 'root' })
export class CodeScanner {
  /** Only in the installed app: a browser has no ML Kit, and the pages say so by hiding the button. */
  readonly available = Capacitor.isNativePlatform();

  /**
   * Opens the scanner and resolves with what the first code says, or null when the user
   * backed out.
   *
   * @throws Error with a message ready to show
   */
  async scan(formats: BarcodeFormat[] = [BarcodeFormat.QrCode]): Promise<string | null> {
    if (!this.available) {
      throw new Error("Le scan n'est disponible que dans l'application mobile.");
    }
    const { supported } = await BarcodeScanner.isSupported();
    if (!supported) {
      throw new Error('Ce téléphone ne peut pas scanner de code : saisissez-le à la main.');
    }
    if (Capacitor.getPlatform() === 'android') {
      await this.ensureAndroidModule();
    } else {
      const { camera } = await BarcodeScanner.requestPermissions();
      if (camera !== 'granted' && camera !== 'limited') {
        throw new Error("Autorisez l'accès à la caméra dans les réglages du téléphone.");
      }
    }
    try {
      const { barcodes } = await BarcodeScanner.scan({ formats });
      return barcodes[0]?.rawValue ?? null;
    } catch (error) {
      // Backing out of the scanner is reported as an error by the plugin; it is not one here.
      if (String((error as Error)?.message ?? error).toLowerCase().includes('cancel')) {
        return null;
      }
      throw new Error('Le scan a échoué. Réessayez, ou saisissez le code à la main.');
    }
  }

  /**
   * Google's scanner is a Play Services module downloaded on first use. Asked for here, and the
   * user is told to try again rather than kept waiting on a download of unknown length.
   */
  private async ensureAndroidModule(): Promise<void> {
    const { available } = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
    if (!available) {
      await BarcodeScanner.installGoogleBarcodeScannerModule();
      throw new Error(
        'Le module de scan Google est en cours d’installation. Réessayez dans quelques secondes.',
      );
    }
  }
}
