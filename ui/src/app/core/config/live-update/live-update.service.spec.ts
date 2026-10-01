import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { ServerApi } from '../../api/server.api';
import { MobileUpdateInfo } from '../../models';
import { ServerConfig } from '../server-config.service';
import { BundleStore, StoredBundle } from './bundle-store';
import { LiveUpdateService } from './live-update.service';

const OFFER: MobileUpdateInfo = {
  available: true,
  version: 'v2',
  checksum: 'abc',
  url: '/api/mobile/bundle/v2.zip',
  sizeBytes: 1000,
  minNativeBuild: 2,
};

/** A phone in memory: records what the service asks of it. */
class FakeBundleStore extends BundleStore {
  available = true;
  current = 'v1';
  build = 2;
  held: StoredBundle | null = null;
  downloads: string[] = [];
  scheduled: string[] = [];
  resumeListener: (() => void) | null = null;

  currentVersion = async () => this.current;
  nativeBuild = async () => this.build;
  find = async () => this.held;
  download = async (url: string, version: string) => {
    this.downloads.push(url);
    return { id: `id-${version}`, version, state: 'ready' as const };
  };
  scheduleNext = async (id: string) => {
    this.scheduled.push(id);
  };
  onResume = (listener: () => void) => {
    this.resumeListener = listener;
  };
}

/** The orchestration: what is asked of the server and of the phone, and when nothing is. */
describe('LiveUpdateService', () => {
  let store: FakeBundleStore;
  let offer: () => Observable<MobileUpdateInfo>;
  let service: LiveUpdateService;

  beforeEach(() => {
    store = new FakeBundleStore();
    offer = () => of(OFFER);
    TestBed.configureTestingModule({
      providers: [
        { provide: BundleStore, useValue: store },
        { provide: ServerApi, useValue: { mobileUpdate: () => offer() } },
        {
          provide: ServerConfig,
          useValue: {
            serverUrl: signal('http://192.168.1.131:8080'),
            apiUrl: (path: string) => `http://192.168.1.131:8080${path}`,
          },
        },
      ],
    });
    service = TestBed.inject(LiveUpdateService);
  });

  it('downloads the new pages from the chosen server and sets them to take over', async () => {
    await service.check();

    expect(store.downloads).toEqual(['http://192.168.1.131:8080/api/mobile/bundle/v2.zip']);
    expect(store.scheduled).toEqual(['id-v2']);
  });

  it('flags the APK as too old, and installs nothing', async () => {
    store.build = 1;

    await service.check();

    expect(service.apkRequired()).toEqual(OFFER);
    expect(store.downloads).toEqual([]);
  });

  it('stays silent when the server cannot be reached', async () => {
    offer = () => throwError(() => new Error('offline'));
    spyOn(console, 'warn');

    await expectAsync(service.check()).toBeResolved();
    expect(store.scheduled).toEqual([]);
  });

  it('runs one check at a time', async () => {
    await Promise.all([service.check(), service.check()]);

    expect(store.downloads.length).toBe(1);
  });

  it('does nothing in a browser', async () => {
    store.available = false;

    service.start();
    await service.check();

    expect(store.resumeListener).toBeNull();
    expect(store.downloads).toEqual([]);
  });

  it('checks again each time the app comes back to the foreground', () => {
    service.start();

    expect(store.resumeListener).not.toBeNull();
  });
});
