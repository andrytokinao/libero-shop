import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ServerApi } from '../../api/server.api';
import { MobileUpdateInfo } from '../../models';
import { ServerConfig } from '../server-config.service';
import { BundleStore } from './bundle-store';
import { UpdateDecision, decideUpdate } from './update-policy';

/**
 * Keeps the phone's pages in step with the server's, without a new APK and without asking.
 *
 * <p>On launch and whenever the app comes back to the foreground, the server is asked which pages
 * it carries; {@link decideUpdate} says what to do, and this carries it out on the
 * {@link BundleStore}. A new version is downloaded in the background, checked against its
 * SHA-256, and takes over the next time the app goes to the background — nobody is interrupted
 * mid-sale. Pages that fail to start are rolled back by the plugin itself (see main.ts).
 *
 * <p>Every failure is silent: no network, an older server without the endpoint, a download cut
 * short. The next foreground tries again; the app as it is keeps working meanwhile.
 */
@Injectable({ providedIn: 'root' })
export class LiveUpdateService {
  private readonly api = inject(ServerApi);
  private readonly server = inject(ServerConfig);
  private readonly store = inject(BundleStore);

  private checking = false;
  private started = false;

  /** Set when the server's pages need a newer APK than the one installed. */
  readonly apkRequired = signal<MobileUpdateInfo | null>(null);

  /** Only on a phone; a no-op in the browser, where the server serves the pages directly. */
  start(): void {
    if (this.started || !this.store.available) {
      return;
    }
    this.started = true;
    void this.check();
    this.store.onResume(() => void this.check());
  }

  /** Also called after the phone is pointed at another server. One check at a time. */
  async check(): Promise<void> {
    if (!this.store.available || this.checking || this.server.serverUrl() === null) {
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
    const decision = decideUpdate({
      offer,
      currentVersion: await this.store.currentVersion(),
      nativeBuild: await this.store.nativeBuild(),
      stored: offer.version ? await this.store.find(offer.version) : null,
    });
    this.apkRequired.set(decision.kind === 'apk-required' ? offer : null);
    await this.carryOut(decision);
  }

  private async carryOut(decision: UpdateDecision): Promise<void> {
    switch (decision.kind) {
      case 'schedule':
        // Takes over when the app next goes to the background: never in the middle of a sale.
        return this.store.scheduleNext(decision.bundleId);
      case 'download': {
        const url = this.server.apiUrl(decision.url);
        const bundle = await this.store.download(url, decision.version, decision.checksum);
        return this.store.scheduleNext(bundle.id);
      }
      case 'up-to-date':
      case 'apk-required':
      case 'skip-failed':
        return;
    }
  }
}
