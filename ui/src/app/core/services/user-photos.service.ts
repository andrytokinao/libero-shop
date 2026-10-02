import { Injectable, Signal, effect, inject, signal, untracked } from '@angular/core';
import { UserPhotoApi } from '../api/user-photo.api';
import { AuthService } from './auth.service';

/**
 * The profile photos on screen, each fetched once.
 *
 * <p>The same seller appears on every row of a list of sales: without this, each row would ask
 * for the same picture. Here the first one asks, and every avatar of that person reads the same
 * signal — null until the picture arrives (initials stand in), then a local object URL.
 *
 * <p>Keyed by account and version: a new photo is a new key, and the old picture is released
 * as soon as the new one is asked for, so a session that sees photos change does not hoard them.
 */
@Injectable({ providedIn: 'root' })
export class UserPhotos {
  private readonly api = inject(UserPhotoApi);
  private readonly urls = new Map<string, ReturnType<typeof signal<string | null>>>();
  /** The version currently held per account, to release the previous one. */
  private readonly current = new Map<number, number>();

  constructor() {
    const auth = inject(AuthService);
    effect(() => {
      if (!auth.isAuthenticated()) {
        untracked(() => this.clear());
      }
    });
  }

  /** The photo's local URL, null while it loads or when it cannot be had. */
  url(userId: number, version: number): Signal<string | null> {
    const key = `${userId}:${version}`;
    let url = this.urls.get(key);
    if (!url) {
      url = signal<string | null>(null);
      this.urls.set(key, url);
      this.releaseOlder(userId, version);
      const target = url;
      this.api.photo(userId, version).subscribe({
        next: (blob) => target.set(URL.createObjectURL(blob)),
        // Gone, or unreachable: the initials stay, which is the right fallback.
        error: () => target.set(null),
      });
    }
    return url.asReadonly();
  }

  /** On sign-out: the next account must not be handed the previous one's pictures. */
  clear(): void {
    this.urls.forEach((url) => this.revoke(url()));
    this.urls.clear();
    this.current.clear();
  }

  private releaseOlder(userId: number, version: number): void {
    const previous = this.current.get(userId);
    this.current.set(userId, version);
    if (previous === undefined || previous === version) {
      return;
    }
    const key = `${userId}:${previous}`;
    this.revoke(this.urls.get(key)?.());
    this.urls.delete(key);
  }

  private revoke(url: string | null | undefined): void {
    if (url) {
      URL.revokeObjectURL(url);
    }
  }
}
