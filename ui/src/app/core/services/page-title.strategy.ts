import { Injectable, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

const APP_NAME = 'Vaha Market';

/**
 * Publishes the active route's `title` so the top bar and the browser tab stay
 * in sync from a single declaration in app.routes.ts.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  readonly pageTitle = signal(APP_NAME);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const routeTitle = this.buildTitle(snapshot);
    this.pageTitle.set(routeTitle ?? APP_NAME);
    this.title.setTitle(routeTitle ? `${routeTitle} — ${APP_NAME}` : APP_NAME);
  }
}
