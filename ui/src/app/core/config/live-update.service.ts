import { Injectable, inject, signal } from '@angular/core';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { CapacitorUpdater } from '@capgo/capacitor-updater';
import { firstValueFrom } from 'rxjs';
import { ServerApi } from '../api/server.api';
import { MobileUpdateInfo } from '../models';
import { ServerConfig } from './server-config.service';

/**
 * Keeps the phone's pages in step with the server's, without a new APK and without asking.
 *
 * <p>On launch and whenever the app comes back to the foreground, the server is asked which pages
 * it carries. A different version is downloaded in the background, checked against its SHA-256,
 * and set to take over the next time the app goes to the background — nobody is interrupted
 * mid-sale, and the next time they open it, it is the new version. Pages that fail to start are
 * rolled back by the plugin itself (see main.ts), and never downloaded again.
 *
 * <p>Pages that need a newer APK than this one — a plugin it lacks — are not installed: the
 * phone keeps working as it is, and `apkRequired` tells the screen to offer the new APK.
 *
 * <p>Every failure is silent: no network, an older server without the endpoint, a download cut
 * short. The next foreground tries again; the app as it is keeps working meanwhile.
 */
@Injectable({ providedIn: 'root' })
export class LiveUpdateService {
  private readonly api = inject(ServerApi);
  private readonly server = inject(ServerConfig);

  private checking = false;
  private started = false;

  /** Set when the server's pages need a newer APK than the one installed. */
  readonly apkRequired = signal<MobileUpdateInfo | null>(null);

  /** Only on a phone; a no-op in the browser, where the server serves the pages directly. */
  start(): void {
    if (this.started || !Capacitor.isNativePlatform()) {
      return;
    }
    this.started = true;
    void this.check();
    void App.addListener('resume', () => void this.check());
  }

  /** Also called after the phone is pointed at another server. */
  async check(): Promise<void> {
    if (this.checking || this.server.serverUrl() === null) {
      return;
    }
    this.checking = true;
    try {
      await this.update();
    } catch (error) {
      console.warn('Mise à jour de l’application reportée :', error);
    } finally {
      this.checking = false;
    }
  }

  private async update(): Promise<void> {
    const offer = await firstValueFrom(this.api.mobileUpdate());
    if (!offer.available || !offer.version || !offer.url) {
      return;
    }
    const { bundle } = await CapacitorUpdater.current();
    if (bundle.version === offer.version) {
      this.apkRequired.set(null);
      return;
    }

    const native = await App.getInfo();
    if (Number(native.build) < offer.minNativeBuild) {
      this.apkRequired.set(offer);
      return;
    }
    this.apkRequired.set(null);

    const { bundles } = await CapacitorUpdater.list();
    const known = bundles.find((candidate) => candidate.version === offer.version);
    if (known?.status === 'error') {
      // Tried before and failed to start: the phone stays on what works until the next version.
      return;
    }
    const usable = known?.status === 'success' || known?.status === 'pending';
    const ready = usable
      ? known
      : await CapacitorUpdater.download({
        url: this.server.apiUrl(offer.url),
        version: offer.version,
        checksum: offer.checksum ?? undefined,
      });
    // Takes over when the app next goes to the background: never in the middle of a sale.
    await CapacitorUpdater.next({ id: ready.id });
  }
}
