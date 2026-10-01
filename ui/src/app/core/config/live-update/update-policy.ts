import { MobileUpdateInfo } from '../../models';
import { StoredBundle } from './bundle-store';

/** What the phone knows when the server's offer arrives. */
export interface UpdateSituation {
  offer: MobileUpdateInfo;
  currentVersion: string;
  nativeBuild: number;
  /** The offered version, if the phone already holds it. */
  stored: StoredBundle | null;
}

export type UpdateDecision =
  /** Nothing to do: no pages offered, or already running them. */
  | { kind: 'up-to-date' }
  /** The pages need a newer APK: keep these, and offer the APK instead. */
  | { kind: 'apk-required' }
  /** This version already failed to start here: stay on what works until the next one. */
  | { kind: 'skip-failed' }
  /** Already downloaded: just set it to take over. */
  | { kind: 'schedule'; bundleId: string }
  /** Fetch it, then set it to take over. */
  | { kind: 'download'; url: string; version: string; checksum: string | null };

/**
 * Whether and how the phone takes the pages the server offers.
 *
 * <p>Pure: no plugin, no network, no clock. Everything that decides is here, in order of
 * precedence, and everything that acts is in LiveUpdateService — so each rule is tested by a line.
 */
export function decideUpdate({ offer, currentVersion, nativeBuild, stored }: UpdateSituation): UpdateDecision {
  if (!offer.available || !offer.version || !offer.url || offer.version === currentVersion) {
    return { kind: 'up-to-date' };
  }
  if (nativeBuild < offer.minNativeBuild) {
    return { kind: 'apk-required' };
  }
  if (stored?.state === 'failed') {
    return { kind: 'skip-failed' };
  }
  if (stored?.state === 'ready') {
    return { kind: 'schedule', bundleId: stored.id };
  }
  return { kind: 'download', url: offer.url, version: offer.version, checksum: offer.checksum };
}
