import { Injectable } from '@angular/core';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { BundleInfo, CapacitorUpdater } from '@capgo/capacitor-updater';
import { BundleStore, StoredBundle } from './bundle-store';

/**
 * {@link BundleStore} on @capgo/capacitor-updater, with the App plugin for the APK's version and
 * the foreground events. The only file that knows either plugin.
 */
@Injectable()
export class CapgoBundleStore extends BundleStore {
  readonly available = Capacitor.isNativePlatform();

  async currentVersion(): Promise<string> {
    return (await CapacitorUpdater.current()).bundle.version;
  }

  async nativeBuild(): Promise<number> {
    return Number((await App.getInfo()).build);
  }

  async find(version: string): Promise<StoredBundle | null> {
    const { bundles } = await CapacitorUpdater.list();
    const found = bundles.find((bundle) => bundle.version === version);
    return found ? toStored(found) : null;
  }

  async download(url: string, version: string, checksum: string | null): Promise<StoredBundle> {
    return toStored(await CapacitorUpdater.download({ url, version, checksum: checksum ?? undefined }));
  }

  async scheduleNext(id: string): Promise<void> {
    await CapacitorUpdater.next({ id });
  }

  onResume(listener: () => void): void {
    void App.addListener('resume', listener);
  }
}

/** The plugin's six statuses, reduced to the three the update logic acts on. */
function toStored(bundle: BundleInfo): StoredBundle {
  const state =
    bundle.status === 'success' || bundle.status === 'pending'
      ? 'ready'
      : bundle.status === 'error'
        ? 'failed'
        : 'unusable';
  return { id: bundle.id, version: bundle.version, state };
}
