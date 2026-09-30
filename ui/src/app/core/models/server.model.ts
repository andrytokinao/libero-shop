/** One address a phone could reach the server at — mirrors ServerConnectionResponse.Address. */
export interface ServerAddress {
  url: string;
  /** A network card's name, or "the address this browser uses". */
  label: string;
  /** Most likely a virtual adapter rather than the shop's Wi-Fi. */
  likelyVirtual: boolean;
  /** What the connection QR code encodes: liberoshop://connect?server=... */
  connectionCode: string;
}

/** The APK published in the server's downloads folder. */
export interface PublishedApk {
  /** Relative to an address, e.g. /downloads/libero-shop.apk */
  path: string;
  sizeBytes: number;
  updatedAt: string;
}

/** GET /api/mobile/update — mirrors MobileUpdateController.UpdateInfo. */
export interface MobileUpdateInfo {
  /** False when the server carries no mobile pages: the app keeps its own. */
  available: boolean;
  version: string | null;
  /** SHA-256 of the zip, checked by the phone after download. */
  checksum: string | null;
  /** Relative to the server, e.g. /api/mobile/bundle/1a2b3c.zip */
  url: string | null;
  sizeBytes: number;
  /** The oldest APK versionCode these pages run in. */
  minNativeBuild: number;
}

/** GET /api/server/connection — mirrors ServerConnectionResponse. */
export interface ServerConnection {
  scheme: string;
  /** On the shop's network. Empty when the caller is not on a local network itself. */
  addresses: ServerAddress[];
  /** For phones away from the shop; null when the server is not published on the Internet. */
  internet: ServerAddress | null;
  /** Null until an APK has been copied to the downloads folder. */
  apk: PublishedApk | null;
}
