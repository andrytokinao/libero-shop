import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';
import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { TitleStrategy, provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { PageTitleStrategy } from './core/services/page-title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(
      // The identity travels in an Authorization header this code sets itself, not in a
      // cookie the browser attaches on its own, so a cross-site request cannot carry it
      // and there is nothing for an anti-CSRF token to protect. Turned off explicitly
      // rather than left on and inert, so the reason is written down.
      withNoXsrfProtection(),
      // authInterceptor first: it adds the header on the way out, errorInterceptor reads
      // the status on the way back. Order matters only in that both must see the call.
      withInterceptors([authInterceptor, errorInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
  ],
};
