import { Signal, signal } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Pictures fetched once each and held as local object URLs, keyed by owner and version.
 *
 * <p>The same picture appears on many tiles at once — a rayon's on its folder, again and again as
 * the seller comes back to it — so the first tile asks and every other reads the same signal: null
 * until the picture arrives (the tile shows its fallback), then the URL. A new version is a new
 * key, and the previous picture of that owner is released as soon as the new one is asked for.
 *
 * <p>A plain class, not a service: each kind of picture owns one, with its own way of fetching.
 */
export class VersionedImages {
  private readonly urls = new Map<string, ReturnType<typeof signal<string | null>>>();
  /** The version currently held per owner, to release the previous one. */
  private readonly current = new Map<number, number>();

  constructor(private readonly fetch: (ownerId: number, version: number) => Observable<Blob>) {}

  /** The picture's local URL, null while it loads or when it cannot be had. */
  url(ownerId: number, version: number): Signal<string | null> {
    const key = `${ownerId}:${version}`;
    let url = this.urls.get(key);
    if (!url) {
      url = signal<string | null>(null);
      this.urls.set(key, url);
      this.releaseOlder(ownerId, version);
      const target = url;
      this.fetch(ownerId, version).subscribe({
        next: (blob) => target.set(URL.createObjectURL(blob)),
        // Gone, or unreachable: the fallback stays, which is the right one.
        error: () => target.set(null),
      });
    }
    return url.asReadonly();
  }

  /** Releases every picture — on sign-out, so the next account starts clean. */
  clear(): void {
    this.urls.forEach((url) => this.revoke(url()));
    this.urls.clear();
    this.current.clear();
  }

  private releaseOlder(ownerId: number, version: number): void {
    const previous = this.current.get(ownerId);
    this.current.set(ownerId, version);
    if (previous === undefined || previous === version) {
      return;
    }
    const key = `${ownerId}:${previous}`;
    this.revoke(this.urls.get(key)?.());
    this.urls.delete(key);
  }

  private revoke(url: string | null | undefined): void {
    if (url) {
      URL.revokeObjectURL(url);
    }
  }
}
