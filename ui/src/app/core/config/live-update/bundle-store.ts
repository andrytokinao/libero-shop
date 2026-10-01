/**
 * A version of the app's pages kept on the phone, in this app's words rather than the plugin's.
 *
 * - `ready`: downloaded and intact, may take over;
 * - `failed`: took over once and did not start — never to be tried again;
 * - `unusable`: half downloaded or being deleted — as good as absent.
 */
export interface StoredBundle {
  id: string;
  version: string;
  state: 'ready' | 'failed' | 'unusable';
}

/**
 * Where the phone keeps its versions of the pages, and how it switches between them.
 *
 * <p>The port the update logic depends on, so that it knows nothing of the plugin doing the work
 * (CapgoBundleStore today): the logic is tested against a fake, and changing plugin is writing
 * another implementation of this, nothing else.
 */
export abstract class BundleStore {
  /** False in a browser, where the server serves the pages itself and there is nothing to store. */
  abstract readonly available: boolean;

  /** The version running now; the plugin's own name for the pages built into the APK otherwise. */
  abstract currentVersion(): Promise<string>;

  /** The installed APK's versionCode. */
  abstract nativeBuild(): Promise<number>;

  abstract find(version: string): Promise<StoredBundle | null>;

  /** Fetches and verifies a version; rejects when the download or the checksum fails. */
  abstract download(url: string, version: string, checksum: string | null): Promise<StoredBundle>;

  /** The version to take over the next time the app goes to the background. */
  abstract scheduleNext(id: string): Promise<void>;

  /** Called each time the app comes back to the foreground. */
  abstract onResume(listener: () => void): void;
}
