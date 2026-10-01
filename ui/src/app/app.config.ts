import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';
import { BundleStore } from './core/config/live-update/bundle-store';
import { CapgoBundleStore } from './core/config/live-update/capgo-bundle-store';
import {
  APP_INITIALIZER,
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideZoneChangeDetection,
} from '@angular/core';
import { TitleStrategy, provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { ServerConfig } from './core/config/server-config.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { serverUrlInterceptor } from './core/interceptors/server-url.interceptor';
import { PageTitleStrategy } from './core/services/page-title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // The phone's page versions, on the Capgo plugin: the one place that names it.
    { provide: BundleStore, useClass: CapgoBundleStore },
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(
      // The token travels in a header the app sets itself, never in a cookie, so there is no
      // cookie for a cross-site request to ride on and nothing for an XSRF token to protect.
      withNoXsrfProtection(),
      // Order matters: the first two decide on the relative `/api` path, the last one sends
      // the request to the configured server.
      withInterceptors([authInterceptor, errorInterceptor, serverUrlInterceptor]),
    ),
    // The server address is read from the device before the first route is evaluated, so no
    // guard and no request ever runs without knowing where the server is.
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const config = inject(ServerConfig);
        return () => config.load();
      },
    },
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
  ],
};
