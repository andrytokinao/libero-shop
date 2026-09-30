import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Preferences } from '@capacitor/preferences';
import { Observable, catchError, map, throwError, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Where the address is kept on the device. */
const STORAGE_KEY = 'liberoshop.server-url';
/** What `/api/server/info` answers when it is really this application — ServerController.APPLICATION. */
const APPLICATION = 'libero-shop';
/** The scheme of the QR codes the administrator's screen shows — ConnectionCode.SCHEME. */
const CONNECTION_SCHEME = 'liberoshop:';
/** Long enough for a phone waking its Wi-Fi, short enough that a wrong address is said quickly. */
const PROBE_TIMEOUT_MS = 6_000;

/** GET /api/server/info — mirrors ServerInfoResponse. */
export interface ServerInfo {
  application: string;
  name: string;
  version: string;
}

/**
 * Which server this installation talks to — the one setting that differs from one shop to the
 * next, and so the one that cannot be in the build.
 *
 * <p>In the web build it is empty and stays so: the pages come from the server, and `/api` on
 * the same origin is the server. In the mobile build the pages are in the phone, and the address
 * is asked at first launch (typed, or scanned from the administrator's QR code), kept on the
 * device, and changeable from the settings.
 *
 * <p>Everything that reaches the server goes through here: `serverUrlInterceptor` prefixes
 * every `/api` call, and `RealtimeService` opens its socket at `socketUrl()`. No other code
 * reads the address, the storage, or the environment to find the server.
 *
 * <p>Kept with Capacitor Preferences — the platform's own settings store on Android, which
 * outlives a web view whose storage the system may clear. In a browser the same API falls back
 * to localStorage, so there is a single code path.
 */
@Injectable({ providedIn: 'root' })
export class ServerConfig {
  /** Bypasses the interceptors: a probe carries no token and must not toast its failures. */
  private readonly bare = new HttpClient(inject(HttpBackend));
  private readonly url = signal<string | null>(null);

  /** The chosen server, e.g. `http://192.168.1.10:8080`; null means "the page's own origin". */
  readonly serverUrl = this.url.asReadonly();
  readonly isMobile = environment.platform === 'mobile';
  /** True when nobody may sign in yet: the mobile app does not know its server. */
  readonly needsSetup = computed(() => environment.requiresServerSetup && this.url() === null);
  /** `192.168.1.10:8080`, for a label; null when there is nothing to show. */
  readonly host = computed(() => {
    const url = this.url();
    return url === null ? null : new URL(url).host;
  });

  /** Run once before the app starts (APP_INITIALIZER), so no request leaves with no address. */
  async load(): Promise<void> {
    const { value } = await Preferences.get({ key: STORAGE_KEY });
    try {
      this.url.set(value ? ServerConfig.normalise(value) : null);
    } catch {
      // An unreadable value must not keep the app from starting: it asks again instead.
      this.url.set(null);
    }
  }

  /**
   * Asks a candidate address who it is. Resolves with its identity when it is a Libero Shop
   * server, fails with a message ready to show otherwise.
   */
  probe(url: string): Observable<ServerInfo> {
    return this.bare.get<ServerInfo>(`${url}/api/server/info`).pipe(
      timeout(PROBE_TIMEOUT_MS),
      catchError(() =>
        throwError(() => new Error(`Aucune réponse de ${url}. Vérifiez l'adresse et que le téléphone est sur le même réseau que le serveur.`)),
      ),
      map((info) => {
        if (info?.application !== APPLICATION) {
          throw new Error(`${url} répond, mais ce n'est pas un serveur Libero Shop.`);
        }
        return info;
      }),
    );
  }

  /** Keeps an address that `probe` accepted. */
  async save(url: string): Promise<void> {
    const normalised = ServerConfig.normalise(url);
    await Preferences.set({ key: STORAGE_KEY, value: normalised });
    this.url.set(normalised);
  }

  /** Back to the page's own origin — meaningful in the web build only. */
  async forget(): Promise<void> {
    await Preferences.remove({ key: STORAGE_KEY });
    this.url.set(null);
  }

  /** `/api/x` → `http://server/api/x`; anything else untouched. */
  apiUrl(url: string): string {
    const base = this.url();
    return base !== null && url.startsWith('/api') ? `${base}${url}` : url;
  }

  /** The WebSocket address for an endpoint such as `/ws`, on the chosen server or the page's. */
  socketUrl(path: string): string {
    const base = this.url();
    if (base !== null) {
      return `${base.replace(/^http/, 'ws')}${path}`;
    }
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}${path}`;
  }

  /**
   * An address as a person types it — "192.168.1.10:8080", "http://boutique.local/", or the
   * page URL copied from a browser with "/api" or a route at the end — reduced to
   * `scheme://host[:port]`.
   *
   * @throws Error with a message ready to show when nothing usable is left
   */
  static normalise(input: string): string {
    let text = input.trim();
    if (!text) {
      throw new Error("Saisissez l'adresse du serveur.");
    }
    if (!/^https?:\/\//i.test(text)) {
      text = `http://${text}`;
    }
    let parsed: URL;
    try {
      parsed = new URL(text);
    } catch {
      throw new Error(`« ${input.trim()} » n'est pas une adresse valide.`);
    }
    if (!parsed.hostname) {
      throw new Error(`« ${input.trim()} » n'est pas une adresse valide.`);
    }
    // Only the origin is kept: the app knows the paths, the address is where to send them.
    return `${parsed.protocol}//${parsed.host}`;
  }

  /**
   * What a scanned QR code says: `liberoshop://connect?server=...` from the administrator's
   * screen, or a plain http(s) address. Anything else — a Wi-Fi code, a menu — is refused.
   *
   * @throws Error with a message ready to show
   */
  static fromScan(text: string): string {
    const scanned = text.trim();
    if (scanned.toLowerCase().startsWith(CONNECTION_SCHEME)) {
      const server = new URL(scanned).searchParams.get('server');
      if (server) {
        return ServerConfig.normalise(server);
      }
    } else if (/^https?:\/\//i.test(scanned)) {
      return ServerConfig.normalise(scanned);
    }
    throw new Error("Ce QR code n'est pas un code de connexion Libero Shop.");
  }
}
