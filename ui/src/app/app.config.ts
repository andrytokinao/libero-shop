import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { TitleStrategy, provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { PageTitleStrategy } from './core/services/page-title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(
      // Matches Spring Security's CookieCsrfTokenRepository: the backend writes the
      // token in XSRF-TOKEN, Angular echoes it back in X-XSRF-TOKEN on every mutating
      // call. Only works because the API is called through a relative path.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([errorInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
  ],
};
