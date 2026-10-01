import { MobileUpdateInfo } from '../../models';
import { StoredBundle } from './bundle-store';
import { UpdateSituation, decideUpdate } from './update-policy';

const OFFER: MobileUpdateInfo = {
  available: true,
  version: 'v2',
  checksum: 'abc',
  url: '/api/mobile/bundle/v2.zip',
  sizeBytes: 1000,
  minNativeBuild: 2,
};

function situation(overrides: Partial<UpdateSituation> = {}): UpdateSituation {
  return { offer: OFFER, currentVersion: 'v1', nativeBuild: 2, stored: null, ...overrides };
}

function stored(state: StoredBundle['state']): StoredBundle {
  return { id: 'b-2', version: 'v2', state };
}

/** Each rule of the update, one line each: no plugin, no network. */
describe('decideUpdate', () => {
  it('downloads a version the phone does not hold', () => {
    expect(decideUpdate(situation())).toEqual({
      kind: 'download',
      url: '/api/mobile/bundle/v2.zip',
      version: 'v2',
      checksum: 'abc',
    });
  });

  it('does nothing when already running the offered pages', () => {
    expect(decideUpdate(situation({ currentVersion: 'v2' })).kind).toBe('up-to-date');
  });

  it('does nothing when the server offers no pages', () => {
    const offer = { ...OFFER, available: false, version: null, url: null };
    expect(decideUpdate(situation({ offer })).kind).toBe('up-to-date');
  });

  it('asks for the new APK rather than install pages it cannot run', () => {
    expect(decideUpdate(situation({ nativeBuild: 1 })).kind).toBe('apk-required');
  });

  it('checks the APK before anything held: a ready download is not installed on too old an APK', () => {
    expect(decideUpdate(situation({ nativeBuild: 1, stored: stored('ready') })).kind).toBe('apk-required');
  });

  it('never retries a version that failed to start here', () => {
    expect(decideUpdate(situation({ stored: stored('failed') })).kind).toBe('skip-failed');
  });

  it('reuses a version already downloaded', () => {
    expect(decideUpdate(situation({ stored: stored('ready') }))).toEqual({ kind: 'schedule', bundleId: 'b-2' });
  });

  it('downloads again a version only half downloaded', () => {
    expect(decideUpdate(situation({ stored: stored('unusable') })).kind).toBe('download');
  });
});
